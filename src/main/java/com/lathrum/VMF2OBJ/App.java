package com.lathrum.VMF2OBJ;

import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
//import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.awt.image.BufferedImage;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.cli.*;

import com.lathrum.VMF2OBJ.fileStructure.*;
import com.lathrum.VMF2OBJ.dataStructure.*;
import com.lathrum.VMF2OBJ.dataStructure.map.*;
import com.lathrum.VMF2OBJ.dataStructure.model.*;
import com.lathrum.VMF2OBJ.dataStructure.texture.*;

public class App
{

//	private Gson gson = new Gson();

	private static final PipedInputStream normal = new PipedInputStream(); // This terrifying looking thing is the bytes
																			// that the console is printing.
	private static final PipedInputStream error = new PipedInputStream(); // We need these both to send them to our GUI
																			// Console. error sends error messages, and
																			// normal sends
																			// everything else

	
	
	private static Process proc;
	private static String VTFLibPath;
	private static String CrowbarLibPath;
	private static boolean quietMode = false;
	private static boolean ignoreTools = false;

	/**
	 * Sets up Crowbar and VTFCmd.
	 * 
	 * @param dir
	 * @throws URISyntaxException
	 */
	public static void extractLibraries(String dir) throws URISyntaxException
	{
		// Spooky scary, but I don't want to reinvent the wheel.
		ArrayList<String> files = new ArrayList<String>();
		File tempFolder = new File(dir);
		tempFolder.mkdirs();
		tempFolder.deleteOnExit();

		// VTFLIB
		// http://nemesis.thewavelength.net/index.php?p=40
		// For converting VTF files to TGA files
		files.add("DevIL.dll"); // VTFLib dependency
		files.add("VTFLib.dll"); // VTFLib dependency
		files.add("VTFCmd.exe"); // VTFLib itself
		// Crowbar
		// https://steamcommunity.com/groups/CrowbarTool
		// https://github.com/UltraTechX/Crowbar-Command-Line
		// For converting MDL files to SMD files
		files.add("CrowbarCommandLineDecomp.exe"); // Crowbar itself

		URI uri = new URI("");
		URI fileURI;

		try
		{
			uri = App.class.getProtectionDomain().getCodeSource().getLocation().toURI();
		} catch (Exception e)
		{
			System.err.println("Exception: " + e);
		}

		for (String el : files)
		{
			ZipFile zipFile;

			try
			{
				zipFile = new ZipFile(new File(uri));
				try
				{
					fileURI = extractFile(zipFile, el, dir);
					switch (el)
					{
					case ("VTFCmd.exe"):
						VTFLibPath = Paths.get(fileURI).toString();
						break;
					case ("CrowbarCommandLineDecomp.exe"):
						CrowbarLibPath = Paths.get(fileURI).toString();
						break;
					}
				} finally
				{
					zipFile.close();
				}
			} catch (Exception e)
			{
				System.err.println("Exception: " + e);
			}
		}
	}

	/**
	 * Unzips files.
	 * 
	 * @param zipFile  - Input zip file to have all of its contents unzipped
	 * @param fileName - Name of file to extract
	 * @param dir      - Location of the zip file
	 * @return - Unzipped contents
	 * @throws IOException - In case somebody screws up and inputs something that
	 *                     doesn't exist
	 */
	public static URI extractFile(ZipFile zipFile, String fileName, String dir) throws IOException
	{
		File tempFile;
		ZipEntry entry;
		InputStream zipStream;
		OutputStream fileStream;

		tempFile = new File(dir + File.separator + fileName);
		tempFile.createNewFile();
		tempFile.deleteOnExit();
		entry = zipFile.getEntry(fileName);

		if (entry == null)
		{ throw new FileNotFoundException("cannot find file: " + fileName + " in archive: " + zipFile.getName()); }

		zipStream = zipFile.getInputStream(entry);
		fileStream = null;

		try
		{
			final byte[] buf;
			int i;

			fileStream = new FileOutputStream(tempFile);
			buf = new byte[1024];
			i = 0;

			while ((i = zipStream.read(buf)) != -1)
			{ fileStream.write(buf, 0, i); }
		} finally
		{
			zipStream.close();
			fileStream.close();
		}

		return (tempFile.toURI());
	}

	/**
	 * Gets the file extension from the raw file itself. Useful if you have the file
	 * but not the file name.
	 * 
	 * @param file - File to get the extension for
	 * @return - The file extension, as a string
	 */
	public static String getFileExtension(File file)
	{
		String fileName = file.getName();
		int dotIndex = fileName.lastIndexOf('.');
		return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
	}

	public static boolean deleteRecursive(File path) throws FileNotFoundException
	{
		// if (!path.exists())
		// throw new FileNotFoundException(path.getAbsolutePath());
		boolean ret = true;
		if (path.isDirectory())
		{
			for (File f : path.listFiles())
			{ ret = ret && deleteRecursive(f); }
		}
		return ret && path.delete();
	}

	public static boolean deleteRecursiveByExtension(File path, String ext) throws FileNotFoundException
	{
		// if (!path.exists())
		// throw new FileNotFoundException(path.getAbsolutePath());
		boolean ret = true;
		if (path.isDirectory())
		{
			for (File f : path.listFiles())
			{ ret = ret && deleteRecursiveByExtension(f, ext); }
		} else
		{
			ret = ret && (getFileExtension(path).matches(ext)) ? path.delete() : true;
		}
		return ret;
	}

	static String readFile(String path) throws IOException
	{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, StandardCharsets.UTF_8);
	}

	public static String formatPath(String res)
	{
		if (res == null)
			return null;
		if (File.separatorChar == '\\')
		{
			// From Windows to Linux/Mac
			return res.replace('/', File.separatorChar);
		} else
		{
			// From Linux/Mac to Windows
			return res.replace('\\', File.separatorChar);
		}
	}

	public static int getEntryIndexByPath(ArrayList<Entry> object, String path)
	{
		for (int i = 0; i < object.size(); i++)
		{
			if (object != null && object.get(i).getFullPath().equalsIgnoreCase(path))
			{ return i; }
			// else{System.out.println(object.get(i).getFullPath());}
		}
		return -1;
	}

	public static ArrayList<Integer> getEntryIndiciesByPattern(ArrayList<Entry> object, String pattern)
	{
		ArrayList<Integer> indicies = new ArrayList<Integer>();
		for (int i = 0; i < object.size(); i++)
		{
			if (object != null && object.get(i).getFullPath().toLowerCase().contains(pattern.toLowerCase()))
			{ indicies.add(i); }
			// else{System.out.println(object.get(i).getFullPath());}
		}
		return indicies;
	}

	public static int getTextureIndexByName(ArrayList<Texture> object, String name)
	{
		for (int i = 0; i < object.size(); i++)
		{
			if (object != null && object.get(i).name.equalsIgnoreCase(name))
			{ return i; }
		}
		return -1;
	}

	public static int getTextureIndexByFileName(ArrayList<Texture> object, String name)
	{
		for (int i = 0; i < object.size(); i++)
		{
			if (object != null && object.get(i).fileName.equalsIgnoreCase(name))
			{ return i; }
		}
		return -1;
	}

	public static ArrayList<Entry> addExtraFiles(String start, File dir)
	{
		ArrayList<Entry> entries = new ArrayList<Entry>();

		try
		{
			File[] files = dir.listFiles();
			for (File file : files)
			{
				if (file.isDirectory())
				{
					String path = file.getCanonicalPath().substring(start.length());
					if (path.charAt(0) == File.separatorChar)
						path = path.substring(1);
					// System.out.println("directory: " + path);
					entries.addAll(addExtraFiles(start, file));
				} else
				{
					String path = file.getCanonicalPath().substring(start.length());
					if (path.charAt(0) == File.separatorChar)
						path = path.substring(1);
					path = path.replaceAll("\\\\", "/");
					// System.out.println("file: " + path);
					if (path.lastIndexOf("/") == -1)
						entries.add(new FileEntry(file.getName().substring(0, file.getName().lastIndexOf('.')),
								getFileExtension(file), "", file.toString()));
					else
						entries.add(new FileEntry(file.getName().substring(0, file.getName().lastIndexOf('.')),
								getFileExtension(file), path.substring(0, path.lastIndexOf("/")), file.toString()));
				}
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return entries;
	}

	
	public PipedOutputStream sendNormal() throws IOException
	{
		PipedOutputStream sendNormal = new PipedOutputStream();
		try {
		sendNormal.connect(normal); // This thing stops the System.out from writing to console
		System.setOut(new PrintStream(sendNormal, true)); // And this thing tells System.out that it should instead write to sendNormal
		                                                  // Then from here we take sendNormal and give it to the GUI Console 
		} catch (java.io.IOException ioe) {
			String s = "Failed to direct System.out to PipedOutputStream setNormal. Details: \n" + ioe.getMessage();
			sendNormal.write(s.getBytes(), 0, s.getBytes().length);
		} catch (SecurityException se) {
			String s = "Failed to direct System.out to PipedOutputStream setNormal. Details: \n" + se.getMessage();
			sendNormal.write(s.getBytes(), 0, s.getBytes().length);
		}
		sendNormal.flush();
		return sendNormal;
	}
	
	public PipedOutputStream sendError() throws IOException
	{
		PipedOutputStream sendError = new PipedOutputStream();
		try {
		sendError.connect(error); // This thing stops the System.err from writing to console
		System.setErr(new PrintStream(sendError, true)); // And this thing tells System.err that it should instead write to sendError
		                                                  // Then from here we take sendNormal and give it to the GUI Console 
		} catch (java.io.IOException ioe) {
			String s = "Failed to direct System.out to PipedOutputStream setNormal. Details: \n" + ioe.getMessage();
			sendError.write(s.getBytes(), 0, s.getBytes().length);
		} catch (SecurityException se) {
			String s = "Failed to direct System.out to PipedOutputStream setNormal. Details: \n" + se.getMessage();
			sendError.write(s.getBytes(), 0, s.getBytes().length);
		}
		sendError.flush();
		return sendError;
	}
	
	public static void printProgressBar(String text)
	{
		if (quietMode)
			return; // Supress warnings
		Terminal terminal;
		int consoleWidth = 80;
		try
		{
			// Issue #42
			// Defaulting to a dumb terminal when a supported terminal can not be correctly
			// created
			// see https://github.com/jline/jline3/issues/291
			terminal = TerminalBuilder.builder().dumb(true).build();
			if (terminal.getWidth() >= 10) // Workaround for issue #23 under IntelliJ
				consoleWidth = terminal.getWidth();
		} catch (IOException ignored)
		{}
		String pad = new String(new char[Math.max(consoleWidth - text.length(), 0)]).replace("\0", " ");

		System.out.println("\r" + text + pad);
	}

	/**
	 * Used if run through GUI. Takes file paths for the VMF, VPK, External Paths,
	 * and converts it to an OBJ at the output path.
	 * 
	 * @param vmfFilePath       - File path input. The VMF file to convert
	 * @param vpkFilePath       - VPK path input. If there's more than 1 VPK,
	 *                          separate each on a new line
	 * @param externalPath      - External path input. If there's more than 1 VPK,
	 *                          separate each on a new line
	 * @param outputPath        - Output path input. Where the OBJ should be put
	 *                          when it's done
	 * @param ignoreToolBrushes - From the checkbox. Adds tool brushes to OBJ file
	 *                          if checked.
	 * @throws Exception - If something goes wrong.
	 */
	public void compile(String vmfFilePath, String vpkFilePath, String externalPath, String outputPath,
			boolean ignoreToolBrushes) throws Exception
	{
		
		
		
		
		
		
		
		
		// Read Geometry
		// Collapse Vertices
		// Write objects
		// Extract Models
		// Extract materials
		// Convert Materials
		// Convert models to SMD
		// Convert models to OBJ
		// Write Models
		// Write Materials

		Scanner in;
		ProgressBarBuilder pbb;
		ArrayList<Entry> vpkEntries = new ArrayList<Entry>();
		PrintWriter objFile;
		PrintWriter materialFile;
		String outPath = "";
		String objName = "";
		String matLibName = "";

		try
		{
			outPath = outputPath;
			objName = outPath + vmfFilePath.substring(vmfFilePath.lastIndexOf("\\"), vmfFilePath.length()-4) + ".obj";
			matLibName = outPath + vmfFilePath.substring(vmfFilePath.lastIndexOf("\\"), vmfFilePath.length()-4) + ".mtl";
			
			if (!externalPath.equals(""))
			{
				if (externalPath.contains("\n")) // Is there more than one external path?
				{
					String[] externalFolders = externalPath.split("\n");
					for (String path : externalFolders)
					{ vpkEntries.addAll(addExtraFiles(path, new File(path))); }
				} else
					vpkEntries.addAll(addExtraFiles(externalPath, new File(externalPath)));
			}

			ignoreTools = ignoreToolBrushes;
		} catch (Exception e)
		{
			System.out.println("Failed when we were loading file paths for everything.");
			System.out.println("Something was probably left blank or newlines were not used to separate entries.");
		}

		// Clean working directory
		try
		{
			deleteRecursiveByExtension(new File(Paths.get(outPath).getParent().resolve("materials").toString()), "vtf");
			deleteRecursive(new File(Paths.get(outPath).getParent().resolve("models").toString()));
		} catch (Exception e)
		{
			// System.err.println("Exception: "+e);
		}

		// Extract Libraries
		try
		{
			extractLibraries(Paths.get(outPath).getParent().resolve("temp").toString());
		} catch (Exception e)
		{
			System.err.println("Exception: " + e);
		}

		//
		// Read VPK
		//

		// Open vpk file
		System.out.println("[1/5] Reading VPK file(s)...");

		if (!vpkFilePath.equals(""))
		{
			if (vpkFilePath.contains("\n")) // Is there more than one vpk path?
			{
				String[] vpkFiles = vpkFilePath.split("\n");
				for (String path : vpkFiles)
				{

					File vpkFile = new File(path);
					VPK vpk = new VPK(vpkFile);
					try
					{
						vpk.load();
					} catch (Exception e)
					{
						System.err.println("Error while loading vpk file: " + e.getMessage());
						return;
					}

					for (Directory directory : vpk.getDirectories())
					{
						for (Entry entry : directory.getEntries())
						{ vpkEntries.add(entry); }
					}
				}
			}
		}

		// Open infile
		File workingFile = new File(vmfFilePath);
		if (!workingFile.exists())
		{
			try
			{
				File directory = new File(workingFile.getParent());
				if (!directory.exists())
				{ directory.mkdirs(); }
				workingFile.createNewFile();
			} catch (IOException e)
			{
				System.out.println("Exception Occured: " + e.toString());
			}
		}

		// Read File
		String text = "";
		try
		{
			text = readFile(vmfFilePath);
		} catch (IOException e)
		{
			System.out.println("Exception Occured: " + e.toString());
		}
		// System.out.println(text);

		try
		{
			File directory = new File(new File(outPath).getParent());
			if (!directory.exists())
			{ directory.mkdirs(); }

			in = new Scanner(new File(vmfFilePath));
			objFile = new PrintWriter(new FileOutputStream(objName));
			materialFile = new PrintWriter(new FileOutputStream(matLibName));
		} catch (IOException e)
		{
			System.err.println("Error while opening file: " + e.getMessage());
			return;
		}

		//
		// Read Geometry
		//

		System.out.println("[2/5] Reading geometry...");

		VMF vmf = VMF.parseVMF(text);
		vmf = VMF.parseSolids(vmf);
		// System.out.println(gson.toJson(vmf));

		//
		// Write brushes
		//

		ArrayList<Vector3> verticies = new ArrayList<Vector3>();
		ArrayList<Face> faces = new ArrayList<Face>();

		ArrayList<String> materials = new ArrayList<String>();
		ArrayList<Texture> textures = new ArrayList<Texture>();
		int vertexOffset = 1;
		int vertexTextureOffset = 1;
		int vertexNormalOffset = 1;
		System.out.println("[3/5] Writing brushes...");

		objFile.println("# Decompiled with VMF2OBJ by Dylancyclone and TheBlizWiz\n");
		objFile.println("mtllib " + matLibName.substring(formatPath(matLibName).lastIndexOf(File.separatorChar) + 1,
				matLibName.length()));

		if (vmf.solids != null)
		{ // There are no brushes in this VMF
			pbb = new ProgressBarBuilder().setStyle(ProgressBarStyle.ASCII).setTaskName("Writing Brushes...")
					.showSpeed();
			for (Solid solid : ProgressBar.wrap(Arrays.asList(vmf.solids), pbb))
			{
				verticies.clear();
				faces.clear();
				materials.clear();

				for (Side side : solid.sides)
				{
					if (ignoreTools && side.material.toLowerCase().contains("tools/"))
					{ continue; }
					materials.add(side.material.trim());
					if (side.dispinfo == null)
					{
						if (Solid.isDisplacementSolid(solid))
							continue;
						for (Vector3 point : side.points)
						{ verticies.add(point); }
					} else
					{
						// Points are defined in this order:
						// 1 4
						// 2 3
						// -or-
						// A D
						// B C
						int startIndex = side.dispinfo.startposition.closestIndex(side.points);
						// Get adjacent points by going around counter-clockwise
						Vector3 ad = side.points[(startIndex + 1) % 4].subtract(side.points[startIndex]);
						Vector3 ab = side.points[(startIndex + 3) % 4].subtract(side.points[startIndex]);
						// System.out.println(ad);
						// System.out.println(ab);
						for (int i = 0; i < side.dispinfo.normals.length; i++) // rows
						{
							for (int j = 0; j < side.dispinfo.normals[0].length; j++) // columns
							{
								Vector3 point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i)))
										.add(side.dispinfo.normals[i][j].multiply(side.dispinfo.distances[i][j]));
								verticies.add(point);
							}
						}
					}
				}

				// TODO: Margin of error?
				Set<Vector3> uniqueVerticies = new HashSet<Vector3>(verticies);
				ArrayList<Vector3> uniqueVerticiesList = new ArrayList<Vector3>(uniqueVerticies);

				Set<String> uniqueMaterials = new HashSet<String>(materials);
				ArrayList<String> uniqueMaterialsList = new ArrayList<String>(uniqueMaterials);

				// Write Faces

				objFile.println("\n");
				objFile.println("o " + solid.id + "\n");

				for (Vector3 e : uniqueVerticiesList)
				{ objFile.println("v " + e.x + " " + e.y + " " + e.z); }

				for (String el : uniqueMaterialsList)
				{
					el = el.toLowerCase();

					// Read File
					String VMTText = "";
					try
					{
						int index = getEntryIndexByPath(vpkEntries, "materials/" + el + ".vmt");
						if (index == -1)
						{
							printProgressBar("Missing Material: " + el);
							continue;
						}
						VMTText = new String(vpkEntries.get(index).readData());
					} catch (IOException e)
					{
						System.out.println("Exception Occured: " + e.toString());
					}

					VMT vmt = new VMT();
					try
					{
						vmt = VMT.parseVMT(VMTText);
					} catch (Exception ex)
					{
						printProgressBar("Failed to parse Material: " + el);
						continue;
					}
					vmt.name = el;
					// System.out.println(gson.toJson(vmt));
					// System.out.println(vmt.basetexture);
					if (vmt.basetexture == null || vmt.basetexture.isEmpty())
					{
						printProgressBar("Material has no texture: " + el);
						continue;
					}
					if (vmt.basetexture.endsWith(".vtf"))
					{
						vmt.basetexture = vmt.basetexture.substring(0, vmt.basetexture.lastIndexOf('.')); // snip the
																											// extension
					}
					int index = getEntryIndexByPath(vpkEntries, "materials/" + vmt.basetexture + ".vtf");
					// System.out.println(index);
					if (index != -1)
					{
						File materialOutPath = new File(outPath);
						materialOutPath = new File(formatPath(
								materialOutPath.getParent() + File.separator + vpkEntries.get(index).getFullPath()));
						if (!materialOutPath.exists())
						{
							try
							{
								File directory = new File(materialOutPath.getParent());
								if (!directory.exists())
								{ directory.mkdirs(); }
							} catch (Exception e)
							{
								System.out.println("Exception Occured: " + e.toString());
							}
							try
							{
								vpkEntries.get(index).extract(materialOutPath);
								String[] command = new String[]
								{ VTFLibPath, "-folder", formatPath(materialOutPath.toString()), "-output",
										formatPath(materialOutPath.getParent()), "-exportformat", "jpg" };

								if (vmt.translucent == 1 || vmt.alphatest == 1)
								{
									command[6] = "tga"; // If the texture is translucent, use the targa format
								}

								proc = Runtime.getRuntime().exec(command);
								proc.waitFor();
								// materialOutPath.delete();
								if (vmt.translucent == 1 || vmt.alphatest == 1)
								{
									materialOutPath = new File(materialOutPath.toString().substring(0,
											materialOutPath.toString().lastIndexOf('.')) + ".tga");
								} else
								{
									materialOutPath = new File(materialOutPath.toString().substring(0,
											materialOutPath.toString().lastIndexOf('.')) + ".jpg");
								}

								int width = 1;
								int height = 1;
								BufferedImage bimg;
								try
								{
									if (vmt.translucent == 1 || vmt.alphatest == 1)
									{
										byte[] fileContent = Files.readAllBytes(materialOutPath.toPath());
										bimg = TargaReader.decode(fileContent);
									} else
									{
										bimg = ImageIO.read(materialOutPath);
									}
									width = bimg.getWidth();
									height = bimg.getHeight();
								} catch (Exception e)
								{
									System.out.println("Cant read Material: " + materialOutPath);
									// System.out.println(e);
								}
								// System.out.println("Adding Material: "+ el);
								textures.add(
										new Texture(el, vmt.basetexture, materialOutPath.toString(), width, height));
							} catch (Exception e)
							{
								System.err.println("Exception on extract: " + e);
							}

							if (vmt.bumpmap != null)
							{ // If the material has a bump map associated with it
								if (vmt.bumpmap.endsWith(".vtf"))
								{
									vmt.bumpmap = vmt.bumpmap.substring(0, vmt.bumpmap.lastIndexOf('.')); // snip the
																											// extension
								}
								// System.out.println("Bump found on "+vmt.basetexture+": "+vmt.bumpmap);
								int bumpMapIndex = getEntryIndexByPath(vpkEntries, "materials/" + vmt.bumpmap + ".vtf");
								// System.out.println(bumpMapIndex);
								if (bumpMapIndex != -1)
								{
									File bumpMapOutPath = new File(outPath);
									bumpMapOutPath = new File(formatPath(bumpMapOutPath.getParent() + File.separator
											+ vpkEntries.get(bumpMapIndex).getFullPath()));
									if (!bumpMapOutPath.exists())
									{
										try
										{
											File directory = new File(bumpMapOutPath.getParent());
											if (!directory.exists())
											{ directory.mkdirs(); }
										} catch (Exception e)
										{
											System.out.println("Exception Occured: " + e.toString());
										}
										try
										{
											vpkEntries.get(bumpMapIndex).extract(bumpMapOutPath);
											String[] command = new String[]
											{ VTFLibPath, "-folder", formatPath(bumpMapOutPath.toString()), "-output",
													formatPath(bumpMapOutPath.getParent()), "-exportformat", "jpg" };
											proc = Runtime.getRuntime().exec(command);
											proc.waitFor();
										} catch (Exception e)
										{
											System.err.println("Exception on extract: " + e);
										}
									}
								}
							}

							materialFile.println("\n" + "newmtl " + el + "\n" + "Ka 1.000 1.000 1.000\n"
									+ "Kd 1.000 1.000 1.000\n" + "Ks 0.000 0.000 0.000\n" + "d 1.0\n" + "illum 2");
							if (vmt.translucent == 1 || vmt.alphatest == 1)
							{
								materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".tga" + "\n"
										+ "map_Kd " + "materials/" + vmt.basetexture + ".tga");
							} else
							{
								materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".jpg" + "\n"
										+ "map_Kd " + "materials/" + vmt.basetexture + ".jpg");
							}
							if (vmt.bumpmap != null)
							{ // If the material has a bump map associated with it
								materialFile.println("map_bump " + "materials/" + vmt.bumpmap + ".jpg");
							}
							materialFile.println();
						} else
						{ // File has already been extracted
							int textureIndex = getTextureIndexByName(textures, el);
							if (textureIndex == -1) // But this is a new material
							{
								textureIndex = getTextureIndexByFileName(textures, vmt.basetexture);
								// System.out.println("Adding Material: "+ el);
								textures.add(new Texture(el, vmt.basetexture, materialOutPath.toString(),
										textures.get(textureIndex).width, textures.get(textureIndex).height));

								materialFile.println("\n" + "newmtl " + el + "\n" + "Ka 1.000 1.000 1.000\n"
										+ "Kd 1.000 1.000 1.000\n" + "Ks 0.000 0.000 0.000\n" + "d 1.0\n" + "illum 2");
								if (vmt.translucent == 1 || vmt.alphatest == 1)
								{
									materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".tga" + "\n"
											+ "map_Kd " + "materials/" + vmt.basetexture + ".tga");
								} else
								{
									materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".jpg" + "\n"
											+ "map_Kd " + "materials/" + vmt.basetexture + ".jpg");
								}
								if (vmt.bumpmap != null)
								{ // If the material has a bump map associated with it
									materialFile.println("map_bump " + "materials/" + vmt.bumpmap + ".jpg");
								}
								materialFile.println();
							}
						}
					} else
					{ // Cant find material
						int textureIndex = getTextureIndexByName(textures, el);
						if (textureIndex == -1) // But this is a new material
						{
							printProgressBar("Missing Material: " + vmt.basetexture);
							textures.add(new Texture(el, vmt.basetexture, "", 1, 1));
						}
					}
				}
				objFile.println();

				for (Side side : solid.sides)
				{
					if (ignoreTools && side.material.toLowerCase().contains("tools/"))
					{ continue; }
					int index = getTextureIndexByName(textures, side.material.trim());
					if (index == -1)
					{ continue; }
					Texture texture = textures.get(index);

					side.uAxisTranslation = side.uAxisTranslation % texture.width;
					side.vAxisTranslation = side.vAxisTranslation % texture.height;

					if (side.uAxisTranslation < -texture.width / 2)
					{ side.uAxisTranslation += texture.width; }

					if (side.vAxisTranslation < -texture.height / 2)
					{ side.vAxisTranslation += texture.height; }

					String buffer = "";

					if (side.dispinfo == null)
					{
						if (Solid.isDisplacementSolid(solid))
							continue;
						for (int i = 0; i < side.points.length; i++)
						{
							double u = Vector3.dot(side.points[i], side.uAxisVector) / (texture.width * side.uAxisScale)
									+ side.uAxisTranslation / texture.width;
							double v = Vector3.dot(side.points[i], side.vAxisVector)
									/ (texture.height * side.vAxisScale) + side.vAxisTranslation / texture.height;
							u = -u + texture.width;
							v = -v + texture.height;
							objFile.println("vt " + u + " " + v);
							buffer += (uniqueVerticiesList.indexOf(side.points[i]) + vertexOffset) + "/"
									+ (i + vertexTextureOffset) + " ";
						}
						faces.add(new Face(buffer, side.material.trim().toLowerCase()));
						vertexTextureOffset += side.points.length;
					} else
					{
						// Points are defined in this order:
						// 1 4
						// 2 3
						// -or-
						// A D
						// B C
						int startIndex = side.dispinfo.startposition.closestIndex(side.points);
						// Get adjacent points by going around counter-clockwise
						Vector3 ad = side.points[(startIndex + 1) % 4].subtract(side.points[startIndex]);
						Vector3 ab = side.points[(startIndex + 3) % 4].subtract(side.points[startIndex]);
						for (int i = 0; i < side.dispinfo.normals.length - 1; i++) // all rows but last
						{
							for (int j = 0; j < side.dispinfo.normals[0].length - 1; j++) // all columns but last
							{
								buffer = "";
								Vector3 point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i)))
										.add(side.dispinfo.normals[i][j].multiply(side.dispinfo.distances[i][j]));
								double u = Vector3.dot(point, side.uAxisVector) / (texture.width * side.uAxisScale)
										+ side.uAxisTranslation / texture.width;
								double v = Vector3.dot(point, side.vAxisVector) / (texture.height * side.vAxisScale)
										+ side.vAxisTranslation / texture.height;
								u = -u + texture.width;
								v = -v + texture.height;
								objFile.println("vt " + u + " " + v);
								buffer += (uniqueVerticiesList.indexOf(point) + vertexOffset) + "/"
										+ ((((side.dispinfo.normals.length - 1) * i) + j) * 4 + vertexTextureOffset)
										+ " ";

								point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i + 1)))
										.add(side.dispinfo.normals[i + 1][j]
												.multiply(side.dispinfo.distances[i + 1][j]));
								u = Vector3.dot(point, side.uAxisVector) / (texture.width * side.uAxisScale)
										+ side.uAxisTranslation / texture.width;
								v = Vector3.dot(point, side.vAxisVector) / (texture.height * side.vAxisScale)
										+ side.vAxisTranslation / texture.height;
								u = -u + texture.width;
								v = -v + texture.height;
								objFile.println("vt " + u + " " + v);
								buffer += (uniqueVerticiesList.indexOf(point) + vertexOffset) + "/"
										+ ((((side.dispinfo.normals.length - 1) * i) + j) * 4 + vertexTextureOffset + 1)
										+ " ";

								point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j + 1)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i + 1)))
										.add(side.dispinfo.normals[i + 1][j + 1]
												.multiply(side.dispinfo.distances[i + 1][j + 1]));
								u = Vector3.dot(point, side.uAxisVector) / (texture.width * side.uAxisScale)
										+ side.uAxisTranslation / texture.width;
								v = Vector3.dot(point, side.vAxisVector) / (texture.height * side.vAxisScale)
										+ side.vAxisTranslation / texture.height;
								u = -u + texture.width;
								v = -v + texture.height;
								objFile.println("vt " + u + " " + v);
								buffer += (uniqueVerticiesList.indexOf(point) + vertexOffset) + "/"
										+ ((((side.dispinfo.normals.length - 1) * i) + j) * 4 + vertexTextureOffset + 2)
										+ " ";

								point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j + 1)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i)))
										.add(side.dispinfo.normals[i][j + 1]
												.multiply(side.dispinfo.distances[i][j + 1]));
								u = Vector3.dot(point, side.uAxisVector) / (texture.width * side.uAxisScale)
										+ side.uAxisTranslation / texture.width;
								v = Vector3.dot(point, side.vAxisVector) / (texture.height * side.vAxisScale)
										+ side.vAxisTranslation / texture.height;
								u = -u + texture.width;
								v = -v + texture.height;
								objFile.println("vt " + u + " " + v);
								buffer += (uniqueVerticiesList.indexOf(point) + vertexOffset) + "/"
										+ ((((side.dispinfo.normals.length - 1) * i) + j) * 4 + vertexTextureOffset + 3)
										+ " ";

								faces.add(new Face(buffer, side.material.trim().toLowerCase()));
							}
						}
						vertexTextureOffset += (side.dispinfo.normals.length - 1)
								* (side.dispinfo.normals[0].length - 1) * 4;
					}
				}
				objFile.println();
				vertexOffset += uniqueVerticiesList.size();

				String lastMaterial = "";
				for (int i = 0; i < faces.size(); i++)
				{
					if (!faces.get(i).material.equals(lastMaterial))
					{ objFile.println("usemtl " + faces.get(i).material); }
					lastMaterial = faces.get(i).material;

					objFile.println("f " + faces.get(i).text);
				}
			}
		}

		//
		// Process Entities
		//

		System.out.println("[4/5] Processing entities...");

		if (vmf.entities != null)
		{ // There are no entities in this VMF
			pbb = new ProgressBarBuilder().setStyle(ProgressBarStyle.ASCII).setTaskName("Processing entities...")
					.showSpeed();
			for (Entity entity : ProgressBar.wrap(Arrays.asList(vmf.entities), pbb))
			{
				if (entity.classname.contains("prop_"))
				{ // If the entity is a prop
					if (entity.model == null)
					{
						printProgressBar("Prop has no model? " + entity.classname);
						continue;
					}
					verticies.clear();
					faces.clear();
					materials.clear();

					ArrayList<Integer> indicies = getEntryIndiciesByPattern(vpkEntries,
							entity.model.substring(0, entity.model.lastIndexOf('.')) + ".");
					indicies.removeAll(getEntryIndiciesByPattern(vpkEntries,
							entity.model.substring(0, entity.model.lastIndexOf('.')) + ".vmt"));
					indicies.removeAll(getEntryIndiciesByPattern(vpkEntries,
							entity.model.substring(0, entity.model.lastIndexOf('.')) + ".vtf"));
					for (int index : indicies)
					{

						if (index != -1)
						{
							File fileOutPath = new File(outPath);
							fileOutPath = new File(formatPath(
									fileOutPath.getParent() + File.separator + vpkEntries.get(index).getFullPath()));
							if (!fileOutPath.exists())
							{
								try
								{
									File directory = new File(fileOutPath.getParent());
									if (!directory.exists())
									{ directory.mkdirs(); }
								} catch (Exception e)
								{
									System.out.println("Exception Occured: " + e.toString());
								}
								try
								{
									vpkEntries.get(index).extract(fileOutPath);
								} catch (Exception e)
								{
									System.err.println("Exception on extract: " + e);
								}
							}
						}
					}

					String[] command = new String[]
					{ CrowbarLibPath, "-p", formatPath(new File(outPath).getParent() + File.separator + entity.model) };

					proc = Runtime.getRuntime().exec(command);
					// BufferedReader reader = new BufferedReader(new
					// InputStreamReader(proc.getInputStream()));
					// String line = "";
					// while ((line = reader.readLine()) != null) {
					// System.out.println(line);
					// }
					BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
					while ((reader.readLine()) != null)
					{}
					proc.waitFor();

					String qcText = "";
					try
					{
						qcText = readFile(new File(outPath).getParent() + File.separator
								+ entity.model.substring(0, entity.model.lastIndexOf('.')) + ".qc"); // This line may
																										// cause errors
																										// if
																										// the qc file
																										// does not have
																										// the
																										// same name as
																										// the mdl file
					} catch (IOException e)
					{
						// System.out.println("Exception Occured: " + e.toString());
					}
					if (qcText.matches(""))
					{
						printProgressBar("Error: Could not find QC file for model, skipping: " + entity.model);
						continue;
					}

					QC qc = QC.parseQC(qcText);

					ArrayList<SMDTriangle> SMDTriangles = new ArrayList<SMDTriangle>();

					for (String bodyGroup : qc.BodyGroups)
					{
						String path = "/";
						if (qc.ModelName.contains("/"))
						{ path += qc.ModelName.substring(0, qc.ModelName.lastIndexOf('/')); }
						String smdText = readFile(formatPath(new File(outPath).getParent() + File.separator + "models"
								+ path + File.separator + bodyGroup));

						SMDTriangles.addAll(Arrays.asList(SMDTriangle.parseSMD(smdText)));
					}

					// Transform model
					String[] angles = entity.angles.split(" ");
					double[] radAngles = new double[3];
					radAngles[0] = Double.parseDouble(angles[0]) * Math.PI / 180;
					radAngles[1] = (Double.parseDouble(angles[1]) + 90) * Math.PI / 180;
					radAngles[2] = Double.parseDouble(angles[2]) * Math.PI / 180;
					double scale = entity.uniformscale != null ? Double.parseDouble(entity.uniformscale) : 1.0;
					String[] origin = entity.origin.split(" ");
					Vector3 transform = new Vector3(Double.parseDouble(origin[0]), Double.parseDouble(origin[1]),
							Double.parseDouble(origin[2]));
					for (int i = 0; i < SMDTriangles.size(); i++)
					{
						SMDTriangle temp = SMDTriangles.get(i);
						materials.add(temp.materialName);
						for (int j = 0; j < temp.points.length; j++)
						{
							// VMF stores rotations as: YZX
							// Source stores relative to obj as: YXZ
							// Meaning translated rotations are: YXZ
							// Or what would normally be read as XZY
							temp.points[j].position = temp.points[j].position.rotate3D(radAngles[0], radAngles[2],
									radAngles[1]);

							temp.points[j].position = temp.points[j].position.multiply(scale);
							temp.points[j].position = temp.points[j].position.add(transform);
							verticies.add(temp.points[j].position);
						}
						SMDTriangles.set(i, temp);
					}

					// TODO: Margin of error?
					Set<Vector3> uniqueVerticies = new HashSet<Vector3>(verticies);
					ArrayList<Vector3> uniqueVerticiesList = new ArrayList<Vector3>(uniqueVerticies);

					Set<String> uniqueMaterials = new HashSet<String>(materials);
					ArrayList<String> uniqueMaterialsList = new ArrayList<String>(uniqueMaterials);

					// Write Faces

					objFile.println("\n");
					objFile.println("o "
							+ qc.ModelName.substring(qc.ModelName.lastIndexOf('/') + 1, qc.ModelName.lastIndexOf('.'))
							+ "\n");

					for (Vector3 e : uniqueVerticiesList)
					{ objFile.println("v " + e.x + " " + e.y + " " + e.z); }

					for (String el : uniqueMaterialsList)
					{
						el = el.toLowerCase();

						// Read File
						String VMTText = "";
						for (String cdMaterial : qc.CDMaterials)
						{ // Our material can be in multiple directories, we gotta find it
							if (cdMaterial.endsWith("/"))
							{ cdMaterial = cdMaterial.substring(0, cdMaterial.lastIndexOf('/')); }
							try
							{
								int index = getEntryIndexByPath(vpkEntries,
										"materials/" + cdMaterial + "/" + el + ".vmt");
								if (index == -1)
								{ continue; } // Could not find it
								VMTText = new String(vpkEntries.get(index).readData());
							} catch (IOException e)
							{
								System.out.println("Exception Occured: " + e.toString());
							}
							if (!VMTText.isEmpty())
							{ break; }
						}
						if (VMTText.isEmpty())
						{
							printProgressBar("Could not find material: " + el);
							continue;
						}

						VMT vmt = new VMT();
						try
						{
							vmt = VMT.parseVMT(VMTText);
						} catch (Exception ex)
						{
							printProgressBar("Failed to parse Material: " + el);
							continue;
						}
						vmt.name = el;
						// System.out.println(gson.toJson(vmt));
						// System.out.println(vmt.basetexture);
						if (vmt.basetexture == null || vmt.basetexture.isEmpty())
						{
							printProgressBar("Material has no texture: " + el);
							continue;
						}
						if (vmt.basetexture.endsWith(".vtf"))
						{
							vmt.basetexture = vmt.basetexture.substring(0, vmt.basetexture.lastIndexOf('.')); // snip
																												// the
																												// extension
						}
						int index = getEntryIndexByPath(vpkEntries, "materials/" + vmt.basetexture + ".vtf");
						// System.out.println(index);
						if (index != -1)
						{
							File materialOutPath = new File(outPath);
							materialOutPath = new File(formatPath(materialOutPath.getParent() + File.separator
									+ vpkEntries.get(index).getFullPath()));
							if (!materialOutPath.exists())
							{
								try
								{
									File directory = new File(materialOutPath.getParent());
									if (!directory.exists())
									{ directory.mkdirs(); }
								} catch (Exception e)
								{
									System.out.println("Exception Occured: " + e.toString());
								}
								try
								{
									vpkEntries.get(index).extract(materialOutPath);
									String[] convertCommand = new String[]
									{ VTFLibPath, "-folder", formatPath(materialOutPath.toString()), "-output",
											formatPath(materialOutPath.getParent()), "-exportformat", "jpg" };

									if (vmt.translucent == 1 || vmt.alphatest == 1)
									{
										convertCommand[6] = "tga"; // If the texture is translucent, use the targa
																	// format
									}

									proc = Runtime.getRuntime().exec(convertCommand);
									proc.waitFor();
									// materialOutPath.delete();
									if (vmt.translucent == 1 || vmt.alphatest == 1)
									{
										materialOutPath = new File(materialOutPath.toString().substring(0,
												materialOutPath.toString().lastIndexOf('.')) + ".tga");
									} else
									{
										materialOutPath = new File(materialOutPath.toString().substring(0,
												materialOutPath.toString().lastIndexOf('.')) + ".jpg");
									}

									int width = 1;
									int height = 1;
									BufferedImage bimg;
									try
									{
										if (vmt.translucent == 1 || vmt.alphatest == 1)
										{
											byte[] fileContent = Files.readAllBytes(materialOutPath.toPath());
											bimg = TargaReader.decode(fileContent);
										} else
										{
											bimg = ImageIO.read(materialOutPath);
										}
										width = bimg.getWidth();
										height = bimg.getHeight();
									} catch (Exception e)
									{
										System.out.println("Cant read Material: " + materialOutPath);
										// System.out.println(e);
									}
									// System.out.println("Adding Material: "+ el);
									textures.add(new Texture(el, vmt.basetexture, materialOutPath.toString(), width,
											height));
								} catch (Exception e)
								{
									System.err.println("Exception on extract: " + e);
								}

								if (vmt.bumpmap != null)
								{ // If the material has a bump map associated with it
									if (vmt.bumpmap.endsWith(".vtf"))
									{
										vmt.bumpmap = vmt.bumpmap.substring(0, vmt.bumpmap.lastIndexOf('.')); // snip
																												// the
																												// extension
									}
									// System.out.println("Bump found on "+vmt.basetexture+": "+vmt.bumpmap);
									int bumpMapIndex = getEntryIndexByPath(vpkEntries,
											"materials/" + vmt.bumpmap + ".vtf");
									// System.out.println(bumpMapIndex);
									if (bumpMapIndex != -1)
									{
										File bumpMapOutPath = new File(outPath);
										bumpMapOutPath = new File(formatPath(bumpMapOutPath.getParent() + File.separator
												+ vpkEntries.get(bumpMapIndex).getFullPath()));
										if (!bumpMapOutPath.exists())
										{
											try
											{
												File directory = new File(bumpMapOutPath.getParent());
												if (!directory.exists())
												{ directory.mkdirs(); }
											} catch (Exception e)
											{
												System.out.println("Exception Occured: " + e.toString());
											}
											try
											{
												vpkEntries.get(bumpMapIndex).extract(bumpMapOutPath);
												String[] convertCommand = new String[]
												{ VTFLibPath, "-folder", formatPath(bumpMapOutPath.toString()),
														"-output", formatPath(bumpMapOutPath.getParent()),
														"-exportformat", "jpg" };
												proc = Runtime.getRuntime().exec(convertCommand);
												proc.waitFor();
											} catch (Exception e)
											{
												System.err.println("Exception on extract: " + e);
											}
										}
									}
								}

								materialFile.println("\n" + "newmtl " + el + "\n" + "Ka 1.000 1.000 1.000\n"
										+ "Kd 1.000 1.000 1.000\n" + "Ks 0.000 0.000 0.000\n" + "d 1.0\n" + "illum 2");
								if (vmt.translucent == 1 || vmt.alphatest == 1)
								{
									materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".tga" + "\n"
											+ "map_Kd " + "materials/" + vmt.basetexture + ".tga");
								} else
								{
									materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".jpg" + "\n"
											+ "map_Kd " + "materials/" + vmt.basetexture + ".jpg");
								}
								if (vmt.bumpmap != null)
								{ // If the material has a bump map associated with it
									materialFile.println("map_bump " + "materials/" + vmt.bumpmap + ".jpg");
								}
								materialFile.println();
							} else
							{ // File has already been extracted
								int textureIndex = getTextureIndexByName(textures, el);
								if (textureIndex == -1) // But this is a new material
								{
									textureIndex = getTextureIndexByFileName(textures, vmt.basetexture);
									// System.out.println("Adding Material: "+ el);
									textures.add(new Texture(el, vmt.basetexture, materialOutPath.toString(),
											textures.get(textureIndex).width, textures.get(textureIndex).height));

									materialFile.println("\n" + "newmtl " + el + "\n" + "Ka 1.000 1.000 1.000\n"
											+ "Kd 1.000 1.000 1.000\n" + "Ks 0.000 0.000 0.000\n" + "d 1.0\n"
											+ "illum 2");
									if (vmt.translucent == 1 || vmt.alphatest == 1)
									{
										materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".tga" + "\n"
												+ "map_Kd " + "materials/" + vmt.basetexture + ".tga");
									} else
									{
										materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".jpg" + "\n"
												+ "map_Kd " + "materials/" + vmt.basetexture + ".jpg");
									}
									if (vmt.bumpmap != null)
									{ // If the material has a bump map associated with it
										materialFile.println("map_bump " + "materials/" + vmt.bumpmap + ".jpg");
									}
									materialFile.println();
								}
							}
						} else
						{ // Cant find material
							int textureIndex = getTextureIndexByName(textures, el);
							if (textureIndex == -1) // But this is a new material
							{
								printProgressBar("Missing Material: " + vmt.basetexture);
								textures.add(new Texture(el, vmt.basetexture, "", 1, 1));
							}
						}
					}
					objFile.println();

					for (SMDTriangle SMDTriangle : SMDTriangles)
					{
						String buffer = "";

						for (int i = 0; i < SMDTriangle.points.length; i++)
						{
							double u = Double.parseDouble(SMDTriangle.points[i].uaxis);
							double v = Double.parseDouble(SMDTriangle.points[i].vaxis);
							objFile.println("vt " + u + " " + v);
							objFile.println("vn " + SMDTriangle.points[i].normal.x + " "
									+ SMDTriangle.points[i].normal.y + " " + SMDTriangle.points[i].normal.z);
							// buffer += (uniqueVerticiesList.indexOf(SMDTriangle.points[i].position) +
							// vertexOffset) + "/"+(i+vertexTextureOffset)+" ";
							buffer += (uniqueVerticiesList.indexOf(SMDTriangle.points[i].position) + vertexOffset) + "/"
									+ (i + vertexTextureOffset) + "/" + (i + vertexNormalOffset) + " ";
						}
						faces.add(new Face(buffer, SMDTriangle.materialName.toLowerCase()));
						vertexTextureOffset += SMDTriangle.points.length;
						vertexNormalOffset += SMDTriangle.points.length;
					}
					objFile.println();
					vertexOffset += uniqueVerticiesList.size();
					String lastMaterial = "";
					for (int i = 0; i < faces.size(); i++)
					{
						if (!faces.get(i).material.equals(lastMaterial))
						{ objFile.println("usemtl " + faces.get(i).material); }
						lastMaterial = faces.get(i).material;

						objFile.println("f " + faces.get(i).text);
					}
				}
			}
		}

		//
		// Clean up
		//

		System.out.println("[5/5] Cleaning up...");

		if (vmf.entities != null)
		{ // There are no entities in this VMF
			deleteRecursive(new File(Paths.get(outPath).getParent().resolve("models").toString())); // Delete models.
																									// Everything is now
																									// in the OBJ file
		}
		deleteRecursiveByExtension(new File(Paths.get(outPath).getParent().resolve("materials").toString()), "vtf"); // Delete
																														// unconverted
																														// textures

		in.close();
		objFile.close();
		materialFile.close();

		System.out.println("Conversion complete! Output can be found at: " + Paths.get(outPath));
	}

	/**
	 * Used if run through command line. Takes file paths for the VMF, VPK, External
	 * Paths, and converts it to an OBJ at the output path.
	 * 
	 * @param args - VMF File path, Output file path, VPK file path(s), External
	 *             file path(s), and command line arguments, in that order
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception
	{
		// Read Geometry
		// Collapse Vertices
		// Write objects
		// Extract Models
		// Extract materials
		// Convert Materials
		// Convert models to SMD
		// Convert models to OBJ
		// Write Models
		// Write Materials

		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption("h", "help", false, "Show this message");
		options.addOption("e", "externalPath", true,
				"Semi-colon separated list of folders for external custom content (such as materials or models)");
		options.addOption("q", "quiet", false, "Suppress warnings");
		options.addOption("t", "tools", false, "Ignore tool brushes");

		Scanner in;
		ProgressBarBuilder pbb;
		ArrayList<Entry> vpkEntries = new ArrayList<Entry>();
		PrintWriter objFile;
		PrintWriter materialFile;
		String outPath = "";
		String objName = "";
		String matLibName = "";

		// Prepare Arguments
		try
		{
			outPath = args[1];
			objName = outPath + ".obj";
			matLibName = outPath + ".mtl";

			// parse the command line arguments
			CommandLine cmd = parser.parse(options, args);
			if (cmd.hasOption("h") || args[0].charAt(0) == '-' || args[1].charAt(0) == '-' || args[2].charAt(0) == '-')
			{
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("vmf2obj [VMF_FILE] [OUTPUT_FILE] [VPK_PATHS] [args...]", options, false);
				System.exit(0);
			}
			if (cmd.hasOption("e"))
			{
				String[] externalFolders = cmd.getOptionValue("e").split(";");
				for (String path : externalFolders)
				{ vpkEntries.addAll(addExtraFiles(path, new File(path))); }
			}
			if (cmd.hasOption("q"))
			{ quietMode = true; }
			if (cmd.hasOption("t"))
			{ ignoreTools = true; }
		} catch (ParseException e)
		{
			System.err.println(e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("vmf2obj [VMF_FILE] [OUTPUT_FILE] [VPK_PATHS] [args...]", options, false);
			System.exit(0);
		} catch (Exception e)
		{
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("vmf2obj [VMF_FILE] [OUTPUT_FILE] [VPK_PATHS] [args...]", options, false);
			System.exit(0);
		}

		// Clean working directory
		try
		{
			deleteRecursiveByExtension(new File(Paths.get(outPath).getParent().resolve("materials").toString()), "vtf");
			deleteRecursive(new File(Paths.get(outPath).getParent().resolve("models").toString()));
		} catch (Exception e)
		{
			// System.err.println("Exception: "+e);
		}

		// Extract Libraries
		try
		{
			extractLibraries(Paths.get(outPath).getParent().resolve("temp").toString());
		} catch (Exception e)
		{
			System.err.println("Exception: " + e);
		}

		//
		// Read VPK
		//

		// Open vpk file
		System.out.println("[1/5] Reading VPK file(s)...");
		String[] vpkFiles = args[2].split(";");
		for (String path : vpkFiles)
		{

			File vpkFile = new File(path);
			VPK vpk = new VPK(vpkFile);
			try
			{
				vpk.load();
			} catch (Exception e)
			{
				System.err.println("Error while loading vpk file: " + e.getMessage());
				return;
			}

			for (Directory directory : vpk.getDirectories())
			{
				for (Entry entry : directory.getEntries())
				{ vpkEntries.add(entry); }
			}
		}

		// Open infile
		File workingFile = new File(args[0]);
		if (!workingFile.exists())
		{
			try
			{
				File directory = new File(workingFile.getParent());
				if (!directory.exists())
				{ directory.mkdirs(); }
				workingFile.createNewFile();
			} catch (IOException e)
			{
				System.out.println("Exception Occured: " + e.toString());
			}
		}

		// Read File
		String text = "";
		try
		{
			text = readFile(args[0]);
		} catch (IOException e)
		{
			System.out.println("Exception Occured: " + e.toString());
		}
		// System.out.println(text);

		try
		{
			File directory = new File(new File(outPath).getParent());
			if (!directory.exists())
			{ directory.mkdirs(); }

			in = new Scanner(new File(args[0]));
			objFile = new PrintWriter(new FileOutputStream(objName));
			materialFile = new PrintWriter(new FileOutputStream(matLibName));
		} catch (IOException e)
		{
			System.err.println("Error while opening file: " + e.getMessage());
			return;
		}

		//
		// Read Geometry
		//

		System.out.println("[2/5] Reading geometry...");

		VMF vmf = VMF.parseVMF(text);
		vmf = VMF.parseSolids(vmf);
		// System.out.println(gson.toJson(vmf));

		//
		// Write brushes
		//

		ArrayList<Vector3> verticies = new ArrayList<Vector3>();
		ArrayList<Face> faces = new ArrayList<Face>();

		ArrayList<String> materials = new ArrayList<String>();
		ArrayList<Texture> textures = new ArrayList<Texture>();
		int vertexOffset = 1;
		int vertexTextureOffset = 1;
		int vertexNormalOffset = 1;
		System.out.println("[3/5] Writing brushes...");

		objFile.println("# Decompiled with VMF2OBJ by Dylancyclone\n");
		objFile.println("mtllib " + matLibName.substring(formatPath(matLibName).lastIndexOf(File.separatorChar) + 1,
				matLibName.length()));

		if (vmf.solids != null)
		{ // There are no brushes in this VMF
			pbb = new ProgressBarBuilder().setStyle(ProgressBarStyle.ASCII).setTaskName("Writing Brushes...")
					.showSpeed();
			for (Solid solid : ProgressBar.wrap(Arrays.asList(vmf.solids), pbb))
			{
				verticies.clear();
				faces.clear();
				materials.clear();

				for (Side side : solid.sides)
				{
					if (ignoreTools && side.material.toLowerCase().contains("tools/"))
					{ continue; }
					materials.add(side.material.trim());
					if (side.dispinfo == null)
					{
						if (Solid.isDisplacementSolid(solid))
							continue;
						for (Vector3 point : side.points)
						{ verticies.add(point); }
					} else
					{
						// Points are defined in this order:
						// 1 4
						// 2 3
						// -or-
						// A D
						// B C
						int startIndex = side.dispinfo.startposition.closestIndex(side.points);
						// Get adjacent points by going around counter-clockwise
						Vector3 ad = side.points[(startIndex + 1) % 4].subtract(side.points[startIndex]);
						Vector3 ab = side.points[(startIndex + 3) % 4].subtract(side.points[startIndex]);
						// System.out.println(ad);
						// System.out.println(ab);
						for (int i = 0; i < side.dispinfo.normals.length; i++) // rows
						{
							for (int j = 0; j < side.dispinfo.normals[0].length; j++) // columns
							{
								Vector3 point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i)))
										.add(side.dispinfo.normals[i][j].multiply(side.dispinfo.distances[i][j]));
								verticies.add(point);
							}
						}
					}
				}

				// TODO: Margin of error?
				Set<Vector3> uniqueVerticies = new HashSet<Vector3>(verticies);
				ArrayList<Vector3> uniqueVerticiesList = new ArrayList<Vector3>(uniqueVerticies);

				Set<String> uniqueMaterials = new HashSet<String>(materials);
				ArrayList<String> uniqueMaterialsList = new ArrayList<String>(uniqueMaterials);

				// Write Faces

				objFile.println("\n");
				objFile.println("o " + solid.id + "\n");

				for (Vector3 e : uniqueVerticiesList)
				{ objFile.println("v " + e.x + " " + e.y + " " + e.z); }

				for (String el : uniqueMaterialsList)
				{
					el = el.toLowerCase();

					// Read File
					String VMTText = "";
					try
					{
						int index = getEntryIndexByPath(vpkEntries, "materials/" + el + ".vmt");
						if (index == -1)
						{
							printProgressBar("Missing Material: " + el);
							continue;
						}
						VMTText = new String(vpkEntries.get(index).readData());
					} catch (IOException e)
					{
						System.out.println("Exception Occured: " + e.toString());
					}

					VMT vmt = new VMT();
					try
					{
						vmt = VMT.parseVMT(VMTText);
					} catch (Exception ex)
					{
						printProgressBar("Failed to parse Material: " + el);
						continue;
					}
					vmt.name = el;
					// System.out.println(gson.toJson(vmt));
					// System.out.println(vmt.basetexture);
					if (vmt.basetexture == null || vmt.basetexture.isEmpty())
					{
						printProgressBar("Material has no texture: " + el);
						continue;
					}
					if (vmt.basetexture.endsWith(".vtf"))
					{
						vmt.basetexture = vmt.basetexture.substring(0, vmt.basetexture.lastIndexOf('.')); // snip the
																											// extension
					}
					int index = getEntryIndexByPath(vpkEntries, "materials/" + vmt.basetexture + ".vtf");
					// System.out.println(index);
					if (index != -1)
					{
						File materialOutPath = new File(outPath);
						materialOutPath = new File(formatPath(
								materialOutPath.getParent() + File.separator + vpkEntries.get(index).getFullPath()));
						if (!materialOutPath.exists())
						{
							try
							{
								File directory = new File(materialOutPath.getParent());
								if (!directory.exists())
								{ directory.mkdirs(); }
							} catch (Exception e)
							{
								System.out.println("Exception Occured: " + e.toString());
							}
							try
							{
								vpkEntries.get(index).extract(materialOutPath);
								String[] command = new String[]
								{ VTFLibPath, "-folder", formatPath(materialOutPath.toString()), "-output",
										formatPath(materialOutPath.getParent()), "-exportformat", "jpg" };

								if (vmt.translucent == 1 || vmt.alphatest == 1)
								{
									command[6] = "tga"; // If the texture is translucent, use the targa format
								}

								proc = Runtime.getRuntime().exec(command);
								proc.waitFor();
								// materialOutPath.delete();
								if (vmt.translucent == 1 || vmt.alphatest == 1)
								{
									materialOutPath = new File(materialOutPath.toString().substring(0,
											materialOutPath.toString().lastIndexOf('.')) + ".tga");
								} else
								{
									materialOutPath = new File(materialOutPath.toString().substring(0,
											materialOutPath.toString().lastIndexOf('.')) + ".jpg");
								}

								int width = 1;
								int height = 1;
								BufferedImage bimg;
								try
								{
									if (vmt.translucent == 1 || vmt.alphatest == 1)
									{
										byte[] fileContent = Files.readAllBytes(materialOutPath.toPath());
										bimg = TargaReader.decode(fileContent);
									} else
									{
										bimg = ImageIO.read(materialOutPath);
									}
									width = bimg.getWidth();
									height = bimg.getHeight();
								} catch (Exception e)
								{
									System.out.println("Cant read Material: " + materialOutPath);
									// System.out.println(e);
								}
								// System.out.println("Adding Material: "+ el);
								textures.add(
										new Texture(el, vmt.basetexture, materialOutPath.toString(), width, height));
							} catch (Exception e)
							{
								System.err.println("Exception on extract: " + e);
							}

							if (vmt.bumpmap != null)
							{ // If the material has a bump map associated with it
								if (vmt.bumpmap.endsWith(".vtf"))
								{
									vmt.bumpmap = vmt.bumpmap.substring(0, vmt.bumpmap.lastIndexOf('.')); // snip the
																											// extension
								}
								// System.out.println("Bump found on "+vmt.basetexture+": "+vmt.bumpmap);
								int bumpMapIndex = getEntryIndexByPath(vpkEntries, "materials/" + vmt.bumpmap + ".vtf");
								// System.out.println(bumpMapIndex);
								if (bumpMapIndex != -1)
								{
									File bumpMapOutPath = new File(outPath);
									bumpMapOutPath = new File(formatPath(bumpMapOutPath.getParent() + File.separator
											+ vpkEntries.get(bumpMapIndex).getFullPath()));
									if (!bumpMapOutPath.exists())
									{
										try
										{
											File directory = new File(bumpMapOutPath.getParent());
											if (!directory.exists())
											{ directory.mkdirs(); }
										} catch (Exception e)
										{
											System.out.println("Exception Occured: " + e.toString());
										}
										try
										{
											vpkEntries.get(bumpMapIndex).extract(bumpMapOutPath);
											String[] command = new String[]
											{ VTFLibPath, "-folder", formatPath(bumpMapOutPath.toString()), "-output",
													formatPath(bumpMapOutPath.getParent()), "-exportformat", "jpg" };
											proc = Runtime.getRuntime().exec(command);
											proc.waitFor();
										} catch (Exception e)
										{
											System.err.println("Exception on extract: " + e);
										}
									}
								}
							}

							materialFile.println("\n" + "newmtl " + el + "\n" + "Ka 1.000 1.000 1.000\n"
									+ "Kd 1.000 1.000 1.000\n" + "Ks 0.000 0.000 0.000\n" + "d 1.0\n" + "illum 2");
							if (vmt.translucent == 1 || vmt.alphatest == 1)
							{
								materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".tga" + "\n"
										+ "map_Kd " + "materials/" + vmt.basetexture + ".tga");
							} else
							{
								materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".jpg" + "\n"
										+ "map_Kd " + "materials/" + vmt.basetexture + ".jpg");
							}
							if (vmt.bumpmap != null)
							{ // If the material has a bump map associated with it
								materialFile.println("map_bump " + "materials/" + vmt.bumpmap + ".jpg");
							}
							materialFile.println();
						} else
						{ // File has already been extracted
							int textureIndex = getTextureIndexByName(textures, el);
							if (textureIndex == -1) // But this is a new material
							{
								textureIndex = getTextureIndexByFileName(textures, vmt.basetexture);
								// System.out.println("Adding Material: "+ el);
								textures.add(new Texture(el, vmt.basetexture, materialOutPath.toString(),
										textures.get(textureIndex).width, textures.get(textureIndex).height));

								materialFile.println("\n" + "newmtl " + el + "\n" + "Ka 1.000 1.000 1.000\n"
										+ "Kd 1.000 1.000 1.000\n" + "Ks 0.000 0.000 0.000\n" + "d 1.0\n" + "illum 2");
								if (vmt.translucent == 1 || vmt.alphatest == 1)
								{
									materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".tga" + "\n"
											+ "map_Kd " + "materials/" + vmt.basetexture + ".tga");
								} else
								{
									materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".jpg" + "\n"
											+ "map_Kd " + "materials/" + vmt.basetexture + ".jpg");
								}
								if (vmt.bumpmap != null)
								{ // If the material has a bump map associated with it
									materialFile.println("map_bump " + "materials/" + vmt.bumpmap + ".jpg");
								}
								materialFile.println();
							}
						}
					} else
					{ // Cant find material
						int textureIndex = getTextureIndexByName(textures, el);
						if (textureIndex == -1) // But this is a new material
						{
							printProgressBar("Missing Material: " + vmt.basetexture);
							textures.add(new Texture(el, vmt.basetexture, "", 1, 1));
						}
					}
				}
				objFile.println();

				for (Side side : solid.sides)
				{
					if (ignoreTools && side.material.toLowerCase().contains("tools/"))
					{ continue; }
					int index = getTextureIndexByName(textures, side.material.trim());
					if (index == -1)
					{ continue; }
					Texture texture = textures.get(index);

					side.uAxisTranslation = side.uAxisTranslation % texture.width;
					side.vAxisTranslation = side.vAxisTranslation % texture.height;

					if (side.uAxisTranslation < -texture.width / 2)
					{ side.uAxisTranslation += texture.width; }

					if (side.vAxisTranslation < -texture.height / 2)
					{ side.vAxisTranslation += texture.height; }

					String buffer = "";

					if (side.dispinfo == null)
					{
						if (Solid.isDisplacementSolid(solid))
							continue;
						for (int i = 0; i < side.points.length; i++)
						{
							double u = Vector3.dot(side.points[i], side.uAxisVector) / (texture.width * side.uAxisScale)
									+ side.uAxisTranslation / texture.width;
							double v = Vector3.dot(side.points[i], side.vAxisVector)
									/ (texture.height * side.vAxisScale) + side.vAxisTranslation / texture.height;
							u = -u + texture.width;
							v = -v + texture.height;
							objFile.println("vt " + u + " " + v);
							buffer += (uniqueVerticiesList.indexOf(side.points[i]) + vertexOffset) + "/"
									+ (i + vertexTextureOffset) + " ";
						}
						faces.add(new Face(buffer, side.material.trim().toLowerCase()));
						vertexTextureOffset += side.points.length;
					} else
					{
						// Points are defined in this order:
						// 1 4
						// 2 3
						// -or-
						// A D
						// B C
						int startIndex = side.dispinfo.startposition.closestIndex(side.points);
						// Get adjacent points by going around counter-clockwise
						Vector3 ad = side.points[(startIndex + 1) % 4].subtract(side.points[startIndex]);
						Vector3 ab = side.points[(startIndex + 3) % 4].subtract(side.points[startIndex]);
						for (int i = 0; i < side.dispinfo.normals.length - 1; i++) // all rows but last
						{
							for (int j = 0; j < side.dispinfo.normals[0].length - 1; j++) // all columns but last
							{
								buffer = "";
								Vector3 point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i)))
										.add(side.dispinfo.normals[i][j].multiply(side.dispinfo.distances[i][j]));
								double u = Vector3.dot(point, side.uAxisVector) / (texture.width * side.uAxisScale)
										+ side.uAxisTranslation / texture.width;
								double v = Vector3.dot(point, side.vAxisVector) / (texture.height * side.vAxisScale)
										+ side.vAxisTranslation / texture.height;
								u = -u + texture.width;
								v = -v + texture.height;
								objFile.println("vt " + u + " " + v);
								buffer += (uniqueVerticiesList.indexOf(point) + vertexOffset) + "/"
										+ ((((side.dispinfo.normals.length - 1) * i) + j) * 4 + vertexTextureOffset)
										+ " ";

								point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i + 1)))
										.add(side.dispinfo.normals[i + 1][j]
												.multiply(side.dispinfo.distances[i + 1][j]));
								u = Vector3.dot(point, side.uAxisVector) / (texture.width * side.uAxisScale)
										+ side.uAxisTranslation / texture.width;
								v = Vector3.dot(point, side.vAxisVector) / (texture.height * side.vAxisScale)
										+ side.vAxisTranslation / texture.height;
								u = -u + texture.width;
								v = -v + texture.height;
								objFile.println("vt " + u + " " + v);
								buffer += (uniqueVerticiesList.indexOf(point) + vertexOffset) + "/"
										+ ((((side.dispinfo.normals.length - 1) * i) + j) * 4 + vertexTextureOffset + 1)
										+ " ";

								point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j + 1)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i + 1)))
										.add(side.dispinfo.normals[i + 1][j + 1]
												.multiply(side.dispinfo.distances[i + 1][j + 1]));
								u = Vector3.dot(point, side.uAxisVector) / (texture.width * side.uAxisScale)
										+ side.uAxisTranslation / texture.width;
								v = Vector3.dot(point, side.vAxisVector) / (texture.height * side.vAxisScale)
										+ side.vAxisTranslation / texture.height;
								u = -u + texture.width;
								v = -v + texture.height;
								objFile.println("vt " + u + " " + v);
								buffer += (uniqueVerticiesList.indexOf(point) + vertexOffset) + "/"
										+ ((((side.dispinfo.normals.length - 1) * i) + j) * 4 + vertexTextureOffset + 2)
										+ " ";

								point = side.points[startIndex]
										.add(ad.normalize().multiply(
												ad.divide(side.dispinfo.normals[0].length - 1).abs().multiply(j + 1)))
										.add(ab.normalize().multiply(
												ab.divide(side.dispinfo.normals.length - 1).abs().multiply(i)))
										.add(side.dispinfo.normals[i][j + 1]
												.multiply(side.dispinfo.distances[i][j + 1]));
								u = Vector3.dot(point, side.uAxisVector) / (texture.width * side.uAxisScale)
										+ side.uAxisTranslation / texture.width;
								v = Vector3.dot(point, side.vAxisVector) / (texture.height * side.vAxisScale)
										+ side.vAxisTranslation / texture.height;
								u = -u + texture.width;
								v = -v + texture.height;
								objFile.println("vt " + u + " " + v);
								buffer += (uniqueVerticiesList.indexOf(point) + vertexOffset) + "/"
										+ ((((side.dispinfo.normals.length - 1) * i) + j) * 4 + vertexTextureOffset + 3)
										+ " ";

								faces.add(new Face(buffer, side.material.trim().toLowerCase()));
							}
						}
						vertexTextureOffset += (side.dispinfo.normals.length - 1)
								* (side.dispinfo.normals[0].length - 1) * 4;
					}
				}
				objFile.println();
				vertexOffset += uniqueVerticiesList.size();

				String lastMaterial = "";
				for (int i = 0; i < faces.size(); i++)
				{
					if (!faces.get(i).material.equals(lastMaterial))
					{ objFile.println("usemtl " + faces.get(i).material); }
					lastMaterial = faces.get(i).material;

					objFile.println("f " + faces.get(i).text);
				}
			}
		}

		//
		// Process Entities
		//

		System.out.println("[4/5] Processing entities...");

		if (vmf.entities != null)
		{ // There are no entities in this VMF
			pbb = new ProgressBarBuilder().setStyle(ProgressBarStyle.ASCII).setTaskName("Processing entities...")
					.showSpeed();
			for (Entity entity : ProgressBar.wrap(Arrays.asList(vmf.entities), pbb))
			{
				if (entity.classname.contains("prop_"))
				{ // If the entity is a prop
					if (entity.model == null)
					{
						printProgressBar("Prop has no model? " + entity.classname);
						continue;
					}
					verticies.clear();
					faces.clear();
					materials.clear();

					ArrayList<Integer> indicies = getEntryIndiciesByPattern(vpkEntries,
							entity.model.substring(0, entity.model.lastIndexOf('.')) + ".");
					indicies.removeAll(getEntryIndiciesByPattern(vpkEntries,
							entity.model.substring(0, entity.model.lastIndexOf('.')) + ".vmt"));
					indicies.removeAll(getEntryIndiciesByPattern(vpkEntries,
							entity.model.substring(0, entity.model.lastIndexOf('.')) + ".vtf"));
					for (int index : indicies)
					{

						if (index != -1)
						{
							File fileOutPath = new File(outPath);
							fileOutPath = new File(formatPath(
									fileOutPath.getParent() + File.separator + vpkEntries.get(index).getFullPath()));
							if (!fileOutPath.exists())
							{
								try
								{
									File directory = new File(fileOutPath.getParent());
									if (!directory.exists())
									{ directory.mkdirs(); }
								} catch (Exception e)
								{
									System.out.println("Exception Occured: " + e.toString());
								}
								try
								{
									vpkEntries.get(index).extract(fileOutPath);
								} catch (Exception e)
								{
									System.err.println("Exception on extract: " + e);
								}
							}
						}
					}

					String[] command = new String[]
					{ CrowbarLibPath, "-p", formatPath(new File(outPath).getParent() + File.separator + entity.model) };

					proc = Runtime.getRuntime().exec(command);
					// BufferedReader reader = new BufferedReader(new
					// InputStreamReader(proc.getInputStream()));
					// String line = "";
					// while ((line = reader.readLine()) != null) {
					// System.out.println(line);
					// }
					BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
					while ((reader.readLine()) != null)
					{}
					proc.waitFor();

					String qcText = "";
					try
					{
						qcText = readFile(new File(outPath).getParent() + File.separator
								+ entity.model.substring(0, entity.model.lastIndexOf('.')) + ".qc"); // This line may
																										// cause errors
																										// if
																										// the qc file
																										// does not have
																										// the
																										// same name as
																										// the mdl file
					} catch (IOException e)
					{
						// System.out.println("Exception Occured: " + e.toString());
					}
					if (qcText.matches(""))
					{
						printProgressBar("Error: Could not find QC file for model, skipping: " + entity.model);
						continue;
					}

					QC qc = QC.parseQC(qcText);

					ArrayList<SMDTriangle> SMDTriangles = new ArrayList<SMDTriangle>();

					for (String bodyGroup : qc.BodyGroups)
					{
						String path = "/";
						if (qc.ModelName.contains("/"))
						{ path += qc.ModelName.substring(0, qc.ModelName.lastIndexOf('/')); }
						String smdText = readFile(formatPath(new File(outPath).getParent() + File.separator + "models"
								+ path + File.separator + bodyGroup));

						SMDTriangles.addAll(Arrays.asList(SMDTriangle.parseSMD(smdText)));
					}

					// Transform model
					String[] angles = entity.angles.split(" ");
					double[] radAngles = new double[3];
					radAngles[0] = Double.parseDouble(angles[0]) * Math.PI / 180;
					radAngles[1] = (Double.parseDouble(angles[1]) + 90) * Math.PI / 180;
					radAngles[2] = Double.parseDouble(angles[2]) * Math.PI / 180;
					double scale = entity.uniformscale != null ? Double.parseDouble(entity.uniformscale) : 1.0;
					String[] origin = entity.origin.split(" ");
					Vector3 transform = new Vector3(Double.parseDouble(origin[0]), Double.parseDouble(origin[1]),
							Double.parseDouble(origin[2]));
					for (int i = 0; i < SMDTriangles.size(); i++)
					{
						SMDTriangle temp = SMDTriangles.get(i);
						materials.add(temp.materialName);
						for (int j = 0; j < temp.points.length; j++)
						{
							// VMF stores rotations as: YZX
							// Source stores relative to obj as: YXZ
							// Meaning translated rotations are: YXZ
							// Or what would normally be read as XZY
							temp.points[j].position = temp.points[j].position.rotate3D(radAngles[0], radAngles[2],
									radAngles[1]);

							temp.points[j].position = temp.points[j].position.multiply(scale);
							temp.points[j].position = temp.points[j].position.add(transform);
							verticies.add(temp.points[j].position);
						}
						SMDTriangles.set(i, temp);
					}

					// TODO: Margin of error?
					Set<Vector3> uniqueVerticies = new HashSet<Vector3>(verticies);
					ArrayList<Vector3> uniqueVerticiesList = new ArrayList<Vector3>(uniqueVerticies);

					Set<String> uniqueMaterials = new HashSet<String>(materials);
					ArrayList<String> uniqueMaterialsList = new ArrayList<String>(uniqueMaterials);

					// Write Faces

					objFile.println("\n");
					objFile.println("o "
							+ qc.ModelName.substring(qc.ModelName.lastIndexOf('/') + 1, qc.ModelName.lastIndexOf('.'))
							+ "\n");

					for (Vector3 e : uniqueVerticiesList)
					{ objFile.println("v " + e.x + " " + e.y + " " + e.z); }

					for (String el : uniqueMaterialsList)
					{
						el = el.toLowerCase();

						// Read File
						String VMTText = "";
						for (String cdMaterial : qc.CDMaterials)
						{ // Our material can be in multiple directories, we gotta find it
							if (cdMaterial.endsWith("/"))
							{ cdMaterial = cdMaterial.substring(0, cdMaterial.lastIndexOf('/')); }
							try
							{
								int index = getEntryIndexByPath(vpkEntries,
										"materials/" + cdMaterial + "/" + el + ".vmt");
								if (index == -1)
								{ continue; } // Could not find it
								VMTText = new String(vpkEntries.get(index).readData());
							} catch (IOException e)
							{
								System.out.println("Exception Occured: " + e.toString());
							}
							if (!VMTText.isEmpty())
							{ break; }
						}
						if (VMTText.isEmpty())
						{
							printProgressBar("Could not find material: " + el);
							continue;
						}

						VMT vmt = new VMT();
						try
						{
							vmt = VMT.parseVMT(VMTText);
						} catch (Exception ex)
						{
							printProgressBar("Failed to parse Material: " + el);
							continue;
						}
						vmt.name = el;
						// System.out.println(gson.toJson(vmt));
						// System.out.println(vmt.basetexture);
						if (vmt.basetexture == null || vmt.basetexture.isEmpty())
						{
							printProgressBar("Material has no texture: " + el);
							continue;
						}
						if (vmt.basetexture.endsWith(".vtf"))
						{
							vmt.basetexture = vmt.basetexture.substring(0, vmt.basetexture.lastIndexOf('.')); // snip
																												// the
																												// extension
						}
						int index = getEntryIndexByPath(vpkEntries, "materials/" + vmt.basetexture + ".vtf");
						// System.out.println(index);
						if (index != -1)
						{
							File materialOutPath = new File(outPath);
							materialOutPath = new File(formatPath(materialOutPath.getParent() + File.separator
									+ vpkEntries.get(index).getFullPath()));
							if (!materialOutPath.exists())
							{
								try
								{
									File directory = new File(materialOutPath.getParent());
									if (!directory.exists())
									{ directory.mkdirs(); }
								} catch (Exception e)
								{
									System.out.println("Exception Occured: " + e.toString());
								}
								try
								{
									vpkEntries.get(index).extract(materialOutPath);
									String[] convertCommand = new String[]
									{ VTFLibPath, "-folder", formatPath(materialOutPath.toString()), "-output",
											formatPath(materialOutPath.getParent()), "-exportformat", "jpg" };

									if (vmt.translucent == 1 || vmt.alphatest == 1)
									{
										convertCommand[6] = "tga"; // If the texture is translucent, use the targa
																	// format
									}

									proc = Runtime.getRuntime().exec(convertCommand);
									proc.waitFor();
									// materialOutPath.delete();
									if (vmt.translucent == 1 || vmt.alphatest == 1)
									{
										materialOutPath = new File(materialOutPath.toString().substring(0,
												materialOutPath.toString().lastIndexOf('.')) + ".tga");
									} else
									{
										materialOutPath = new File(materialOutPath.toString().substring(0,
												materialOutPath.toString().lastIndexOf('.')) + ".jpg");
									}

									int width = 1;
									int height = 1;
									BufferedImage bimg;
									try
									{
										if (vmt.translucent == 1 || vmt.alphatest == 1)
										{
											byte[] fileContent = Files.readAllBytes(materialOutPath.toPath());
											bimg = TargaReader.decode(fileContent);
										} else
										{
											bimg = ImageIO.read(materialOutPath);
										}
										width = bimg.getWidth();
										height = bimg.getHeight();
									} catch (Exception e)
									{
										System.out.println("Cant read Material: " + materialOutPath);
										// System.out.println(e);
									}
									// System.out.println("Adding Material: "+ el);
									textures.add(new Texture(el, vmt.basetexture, materialOutPath.toString(), width,
											height));
								} catch (Exception e)
								{
									System.err.println("Exception on extract: " + e);
								}

								if (vmt.bumpmap != null)
								{ // If the material has a bump map associated with it
									if (vmt.bumpmap.endsWith(".vtf"))
									{
										vmt.bumpmap = vmt.bumpmap.substring(0, vmt.bumpmap.lastIndexOf('.')); // snip
																												// the
																												// extension
									}
									// System.out.println("Bump found on "+vmt.basetexture+": "+vmt.bumpmap);
									int bumpMapIndex = getEntryIndexByPath(vpkEntries,
											"materials/" + vmt.bumpmap + ".vtf");
									// System.out.println(bumpMapIndex);
									if (bumpMapIndex != -1)
									{
										File bumpMapOutPath = new File(outPath);
										bumpMapOutPath = new File(formatPath(bumpMapOutPath.getParent() + File.separator
												+ vpkEntries.get(bumpMapIndex).getFullPath()));
										if (!bumpMapOutPath.exists())
										{
											try
											{
												File directory = new File(bumpMapOutPath.getParent());
												if (!directory.exists())
												{ directory.mkdirs(); }
											} catch (Exception e)
											{
												System.out.println("Exception Occured: " + e.toString());
											}
											try
											{
												vpkEntries.get(bumpMapIndex).extract(bumpMapOutPath);
												String[] convertCommand = new String[]
												{ VTFLibPath, "-folder", formatPath(bumpMapOutPath.toString()),
														"-output", formatPath(bumpMapOutPath.getParent()),
														"-exportformat", "jpg" };
												proc = Runtime.getRuntime().exec(convertCommand);
												proc.waitFor();
											} catch (Exception e)
											{
												System.err.println("Exception on extract: " + e);
											}
										}
									}
								}

								materialFile.println("\n" + "newmtl " + el + "\n" + "Ka 1.000 1.000 1.000\n"
										+ "Kd 1.000 1.000 1.000\n" + "Ks 0.000 0.000 0.000\n" + "d 1.0\n" + "illum 2");
								if (vmt.translucent == 1 || vmt.alphatest == 1)
								{
									materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".tga" + "\n"
											+ "map_Kd " + "materials/" + vmt.basetexture + ".tga");
								} else
								{
									materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".jpg" + "\n"
											+ "map_Kd " + "materials/" + vmt.basetexture + ".jpg");
								}
								if (vmt.bumpmap != null)
								{ // If the material has a bump map associated with it
									materialFile.println("map_bump " + "materials/" + vmt.bumpmap + ".jpg");
								}
								materialFile.println();
							} else
							{ // File has already been extracted
								int textureIndex = getTextureIndexByName(textures, el);
								if (textureIndex == -1) // But this is a new material
								{
									textureIndex = getTextureIndexByFileName(textures, vmt.basetexture);
									// System.out.println("Adding Material: "+ el);
									textures.add(new Texture(el, vmt.basetexture, materialOutPath.toString(),
											textures.get(textureIndex).width, textures.get(textureIndex).height));

									materialFile.println("\n" + "newmtl " + el + "\n" + "Ka 1.000 1.000 1.000\n"
											+ "Kd 1.000 1.000 1.000\n" + "Ks 0.000 0.000 0.000\n" + "d 1.0\n"
											+ "illum 2");
									if (vmt.translucent == 1 || vmt.alphatest == 1)
									{
										materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".tga" + "\n"
												+ "map_Kd " + "materials/" + vmt.basetexture + ".tga");
									} else
									{
										materialFile.println("map_Ka " + "materials/" + vmt.basetexture + ".jpg" + "\n"
												+ "map_Kd " + "materials/" + vmt.basetexture + ".jpg");
									}
									if (vmt.bumpmap != null)
									{ // If the material has a bump map associated with it
										materialFile.println("map_bump " + "materials/" + vmt.bumpmap + ".jpg");
									}
									materialFile.println();
								}
							}
						} else
						{ // Cant find material
							int textureIndex = getTextureIndexByName(textures, el);
							if (textureIndex == -1) // But this is a new material
							{
								printProgressBar("Missing Material: " + vmt.basetexture);
								textures.add(new Texture(el, vmt.basetexture, "", 1, 1));
							}
						}
					}
					objFile.println();

					for (SMDTriangle SMDTriangle : SMDTriangles)
					{
						String buffer = "";

						for (int i = 0; i < SMDTriangle.points.length; i++)
						{
							double u = Double.parseDouble(SMDTriangle.points[i].uaxis);
							double v = Double.parseDouble(SMDTriangle.points[i].vaxis);
							objFile.println("vt " + u + " " + v);
							objFile.println("vn " + SMDTriangle.points[i].normal.x + " "
									+ SMDTriangle.points[i].normal.y + " " + SMDTriangle.points[i].normal.z);
							// buffer += (uniqueVerticiesList.indexOf(SMDTriangle.points[i].position) +
							// vertexOffset) + "/"+(i+vertexTextureOffset)+" ";
							buffer += (uniqueVerticiesList.indexOf(SMDTriangle.points[i].position) + vertexOffset) + "/"
									+ (i + vertexTextureOffset) + "/" + (i + vertexNormalOffset) + " ";
						}
						faces.add(new Face(buffer, SMDTriangle.materialName.toLowerCase()));
						vertexTextureOffset += SMDTriangle.points.length;
						vertexNormalOffset += SMDTriangle.points.length;
					}
					objFile.println();
					vertexOffset += uniqueVerticiesList.size();
					String lastMaterial = "";
					for (int i = 0; i < faces.size(); i++)
					{
						if (!faces.get(i).material.equals(lastMaterial))
						{ objFile.println("usemtl " + faces.get(i).material); }
						lastMaterial = faces.get(i).material;

						objFile.println("f " + faces.get(i).text);
					}
				}
			}
		}

		//
		// Clean up
		//

		System.out.println("[5/5] Cleaning up...");

		if (vmf.entities != null)
		{ // There are no entities in this VMF
			deleteRecursive(new File(Paths.get(outPath).getParent().resolve("models").toString())); // Delete models.
																									// Everything is now
																									// in the OBJ file
		}
		deleteRecursiveByExtension(new File(Paths.get(outPath).getParent().resolve("materials").toString()), "vtf"); // Delete
																														// unconverted
																														// textures

		in.close();
		objFile.close();
		materialFile.close();

		System.out.println("Conversion complete! Output can be found at: " + Paths.get(outPath).getParent());
	}
}
