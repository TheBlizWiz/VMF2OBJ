package com.lathrum.VMF2OBJ;

import java.awt.EventQueue;

import javax.swing.JFrame;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import java.awt.Toolkit;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class GUI
{

	private JFrame frame_VMF2OBJ;
	private JTextArea vmf, vpk, ext, out; // It would be nice if we could have these all as local variables, but alas we
											// can't get the text back out if we do that
	private JCheckBox tools;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					GUI window = new GUI();
					Console console = new Console();
					console.getConsole().setVisible(true);
					window.frame_VMF2OBJ.setVisible(true);
				} catch (Exception e)
				{
					e.printStackTrace();
					System.out.println(
							"We couldn't get the window to display. Something must have gone *really* wrong somewhere.");
				}
			}
		});

	}

	/**
	 * Create the application.
	 */
	public GUI()
	{ initialize(); }

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize()
	{
		frame_VMF2OBJ = new JFrame(); // Creates the visible window on screen
		frame_VMF2OBJ.setTitle("VMF2OBJ"); // Sets application name
		//frame_VMF2OBJ.setIconImage(Toolkit.getDefaultToolkit().getImage(GUI.class.getResource("icon.png")));
		frame_VMF2OBJ.setBounds(100, 100, 640, 400); // Sets size of the window
		frame_VMF2OBJ.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Ends the program if you press the X button
		GridBagLayout gridBagLayout = new GridBagLayout(); // All this below until the try statement just sets up the
															// constraints
		gridBagLayout.columnWidths = new int[]
		{ 90, 0, 0, 0 };
		gridBagLayout.rowHeights = new int[]
		{ 0, 0, 0, 0, 0, 0, 0, 0 };
		gridBagLayout.columnWeights = new double[]
		{ 0.0, 1.0, 0.0, Double.MIN_VALUE };
		gridBagLayout.rowWeights = new double[]
		{ 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0, Double.MIN_VALUE };
		frame_VMF2OBJ.getContentPane().setLayout(gridBagLayout);

		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); // App will copy default OS theme. In
																					// this case, Windows
		} catch (UnsupportedLookAndFeelException e)
		{} catch (ClassNotFoundException e)
		{} catch (InstantiationException e)
		{} catch (IllegalAccessException e)
		{}

		JLabel label_logo = new JLabel("");
		//label_logo.setIcon(new ImageIcon(GUI.class.getResource("logo.png"))); // Gotta put a big logo
																				// somewhere, right?
		GridBagConstraints gbc_label_logo = new GridBagConstraints();
		gbc_label_logo.insets = new Insets(0, 0, 5, 5);
		gbc_label_logo.gridx = 1;
		gbc_label_logo.gridy = 0;
		frame_VMF2OBJ.getContentPane().add(label_logo, gbc_label_logo);

		JLabel lblSeparate = new JLabel(
				"Separate multiple file paths by putting them on separate lines. VMF File Path and Output File Path can only have one entry.");
		GridBagConstraints gbc_lblSeparate = new GridBagConstraints();
		gbc_lblSeparate.gridwidth = 3;
		gbc_lblSeparate.insets = new Insets(0, 0, 5, 0);
		gbc_lblSeparate.gridx = 0;
		gbc_lblSeparate.gridy = 1;
		frame_VMF2OBJ.getContentPane().add(lblSeparate, gbc_lblSeparate);

		JLabel lblVmfFilePath = new JLabel("VMF File Path");
		GridBagConstraints gbc_lblVmfFilePath = new GridBagConstraints();
		gbc_lblVmfFilePath.insets = new Insets(0, 0, 5, 5);
		gbc_lblVmfFilePath.gridx = 0;
		gbc_lblVmfFilePath.gridy = 2;
		frame_VMF2OBJ.getContentPane().add(lblVmfFilePath, gbc_lblVmfFilePath);

		vmf = new JTextArea();
		GridBagConstraints gbc_input_VMFFilePath = new GridBagConstraints();
		gbc_input_VMFFilePath.insets = new Insets(0, 0, 5, 5);
		gbc_input_VMFFilePath.fill = GridBagConstraints.BOTH;
		gbc_input_VMFFilePath.gridx = 1;
		gbc_input_VMFFilePath.gridy = 2;
		frame_VMF2OBJ.getContentPane().add(vmf, gbc_input_VMFFilePath);

		JLabel lblVpkFilePaths = new JLabel("VPK File Path(s)");
		GridBagConstraints gbc_lblVpkFilePaths = new GridBagConstraints();
		gbc_lblVpkFilePaths.insets = new Insets(0, 0, 5, 5);
		gbc_lblVpkFilePaths.gridx = 0;
		gbc_lblVpkFilePaths.gridy = 3;
		frame_VMF2OBJ.getContentPane().add(lblVpkFilePaths, gbc_lblVpkFilePaths);

		vpk = new JTextArea();
		GridBagConstraints gbc_input_VPKFilePath = new GridBagConstraints();
		gbc_input_VPKFilePath.insets = new Insets(0, 0, 5, 5);
		gbc_input_VPKFilePath.fill = GridBagConstraints.BOTH;
		gbc_input_VPKFilePath.gridx = 1;
		gbc_input_VPKFilePath.gridy = 3;
		frame_VMF2OBJ.getContentPane().add(vpk, gbc_input_VPKFilePath);

		JLabel lblExternalPaths = new JLabel("External Path(s)");
		GridBagConstraints gbc_lblExternalPaths = new GridBagConstraints();
		gbc_lblExternalPaths.insets = new Insets(0, 0, 5, 5);
		gbc_lblExternalPaths.gridx = 0;
		gbc_lblExternalPaths.gridy = 4;
		frame_VMF2OBJ.getContentPane().add(lblExternalPaths, gbc_lblExternalPaths);

		ext = new JTextArea();
		GridBagConstraints gbc_input_externalFilePath = new GridBagConstraints();
		gbc_input_externalFilePath.insets = new Insets(0, 0, 5, 5);
		gbc_input_externalFilePath.fill = GridBagConstraints.BOTH;
		gbc_input_externalFilePath.gridx = 1;
		gbc_input_externalFilePath.gridy = 4;
		frame_VMF2OBJ.getContentPane().add(ext, gbc_input_externalFilePath);

		JLabel lblOutputFilePath = new JLabel("Output File Path");
		GridBagConstraints gbc_lblOutputFilePath = new GridBagConstraints();
		gbc_lblOutputFilePath.insets = new Insets(0, 0, 5, 5);
		gbc_lblOutputFilePath.gridx = 0;
		gbc_lblOutputFilePath.gridy = 5;
		frame_VMF2OBJ.getContentPane().add(lblOutputFilePath, gbc_lblOutputFilePath);

		out = new JTextArea();
		GridBagConstraints gbc_input_outputFilePath = new GridBagConstraints();
		gbc_input_outputFilePath.insets = new Insets(0, 0, 5, 5);
		gbc_input_outputFilePath.fill = GridBagConstraints.BOTH;
		gbc_input_outputFilePath.gridx = 1;
		gbc_input_outputFilePath.gridy = 5;
		frame_VMF2OBJ.getContentPane().add(out, gbc_input_outputFilePath);

		JButton btnTestTextBoxes = new JButton("Compile");
		btnTestTextBoxes.addActionListener(new ActionListener()
		{
			// TODO: plz work
			public void actionPerformed(ActionEvent e)
			{
				System.out.println("VMF: " + vmf.getText());
				System.out.println("VPK: " + vpk.getText());
				System.out.println("External: " + ext.getText());
				System.out.println("Output: " + out.getText());
				System.out.println("Tool brushes: " + tools.isSelected());

				App vmf2obj = new App();

				try
				{
					vmf2obj.compile(vmf.getText(), vpk.getText(), ext.getText(), out.getText(), tools.isSelected());
				} catch (Exception f)
				{

					f.printStackTrace();
				}
			}
		});
		btnTestTextBoxes.setFont(UIManager.getFont("Button.font"));
		GridBagConstraints gbc_btnTestTextBoxes = new GridBagConstraints();
		gbc_btnTestTextBoxes.insets = new Insets(0, 0, 0, 5);
		gbc_btnTestTextBoxes.gridx = 0;
		gbc_btnTestTextBoxes.gridy = 6;
		frame_VMF2OBJ.getContentPane().add(btnTestTextBoxes, gbc_btnTestTextBoxes);

		tools = new JCheckBox("Ignore tool brushes (e.g. clip, hint, skip)");
		tools.setHorizontalAlignment(SwingConstants.CENTER);
		GridBagConstraints gbc_checkmark_toggleToolBrushes = new GridBagConstraints();
		gbc_checkmark_toggleToolBrushes.insets = new Insets(0, 0, 0, 5);
		gbc_checkmark_toggleToolBrushes.anchor = GridBagConstraints.WEST;
		gbc_checkmark_toggleToolBrushes.gridx = 1;
		gbc_checkmark_toggleToolBrushes.gridy = 6;
		frame_VMF2OBJ.getContentPane().add(tools, gbc_checkmark_toggleToolBrushes);
	}

}
