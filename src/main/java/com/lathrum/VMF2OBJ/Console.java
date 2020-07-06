package com.lathrum.VMF2OBJ;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class Console extends WindowAdapter implements WindowListener, ActionListener, Runnable
{
	private JFrame frame_VMF2OBJConsole;
	private JTextArea text_Console;
	private Thread reader;
	private Thread reader2;
	private boolean quit;

	private final PipedInputStream pin = new PipedInputStream();
	private final PipedInputStream pin2 = new PipedInputStream();

	public Console()
	{
		// create all components and add them
		frame_VMF2OBJConsole = new JFrame("Java Console");
		frame_VMF2OBJConsole.setIconImage(
				Toolkit.getDefaultToolkit().getImage(Console.class.getResource("/resources/icon.png")));
		frame_VMF2OBJConsole.setTitle("VMF2OBJ Console");
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension frameSize = new Dimension((int) (screenSize.width / 2), (int) (screenSize.height / 2));
		int x = (int) (frameSize.width / 2);
		int y = (int) (frameSize.height / 2);
		frame_VMF2OBJConsole.setBounds(x, y, 600, 400);

		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); // App will copy default OS theme. In
																					// this case, Windows
		} catch (UnsupportedLookAndFeelException e)
		{} catch (ClassNotFoundException e)
		{} catch (InstantiationException e)
		{} catch (IllegalAccessException e)
		{}

		text_Console = new JTextArea();
		text_Console.setText("");
		text_Console.setForeground(Color.WHITE);
		text_Console.setFont(new Font("Consolas", Font.PLAIN, 16));
		text_Console.setBackground(Color.BLACK);
		text_Console.setEditable(false);
		JButton button = new JButton("clear");

		frame_VMF2OBJConsole.getContentPane().setLayout(new BorderLayout());
		frame_VMF2OBJConsole.getContentPane().add(new JScrollPane(text_Console), BorderLayout.CENTER);
		frame_VMF2OBJConsole.getContentPane().add(button, BorderLayout.SOUTH);
		frame_VMF2OBJConsole.setVisible(true);

		frame_VMF2OBJConsole.addWindowListener(this);
		button.addActionListener(this);

		try
		{
			PipedOutputStream pout = new PipedOutputStream(this.pin);
			System.setOut(new PrintStream(pout, true));
		} catch (java.io.IOException io)
		{
			text_Console.append("Couldn't redirect STDOUT to this console\n" + io.getMessage());
		} catch (SecurityException se)
		{
			text_Console.append("Couldn't redirect STDOUT to this console\n" + se.getMessage());
		}

		try
		{
			PipedOutputStream pout2 = new PipedOutputStream(this.pin2);
			System.setErr(new PrintStream(pout2, true));
		} catch (java.io.IOException io)
		{
			text_Console.append("Couldn't redirect STDERR to this console\n" + io.getMessage());
		} catch (SecurityException se)
		{
			text_Console.append("Couldn't redirect STDERR to this console\n" + se.getMessage());
		}

		quit = false; // signals the Threads that they should exit

		// Starting two separate threads to read from the PipedInputStreams
		// We do it with a seperate Thread becasue we don't wan't to break a Thread used
		// by the Console.
		reader = new Thread(this);
		reader.setDaemon(true);
		reader.start();
		//
		reader2 = new Thread(this);
		reader2.setDaemon(true);
		reader2.start();
	}

	public void setStreams(PipedInputStream normal, PipedInputStream error)
	{
		try
		{
			PipedOutputStream appNormalStream = new PipedOutputStream(normal);
			System.setOut(new PrintStream(appNormalStream, true));
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		try
		{
			PipedOutputStream appErrorStream = new PipedOutputStream(error);
			System.setErr(new PrintStream(appErrorStream, true));
		} catch (Exception f)
		{
			f.printStackTrace();
		}

	}

	public JFrame getConsole()
	{ return frame_VMF2OBJConsole; }

	public synchronized void windowClosed(WindowEvent evt)
	{
		quit = true;
		this.notifyAll(); // stop all threads
		try
		{
			reader.join(1000);
			pin.close();
		} catch (Exception e)
		{}
		try
		{
			reader2.join(1000);
			pin2.close();
		} catch (Exception e)
		{}
		System.exit(0);
	}

	public synchronized void windowClosing(WindowEvent evt)
	{
		frame_VMF2OBJConsole.setVisible(false); // default behaviour of JFrame
		frame_VMF2OBJConsole.dispose();
	}

	public synchronized void actionPerformed(ActionEvent evt)
	{ text_Console.setText(""); }

	public synchronized void run()
	{
		try
		{
			while (Thread.currentThread() == reader)
			{
				try
				{
					this.wait(100);
				} catch (InterruptedException ie)
				{}
				if (pin.available() != 0)
				{
					String input = this.readLine(pin);
					text_Console.setText(text_Console.getText() + input);
				}
				if (quit)
					return;
			}

			while (Thread.currentThread() == reader2)
			{
				try
				{
					this.wait(100);
				} catch (InterruptedException ie)
				{}
				if (pin2.available() != 0)
				{
					String input = this.readLine(pin2);
					text_Console.setText(text_Console.getText() + input);
				}
				if (quit)
					return;
			}
		} catch (Exception e)
		{
			text_Console.append("\nConsole reports an Internal error.");
			text_Console.append("The error is: " + e);
		}

	}

	public synchronized String readLine(PipedInputStream in) throws IOException
	{
		String input = "";
		do
		{
			int available = in.available();
			if (available == 0)
				break;
			byte b[] = new byte[available];
			in.read(b);
			input = input + new String(b, 0, b.length);
		} while (!input.endsWith("\n") && !input.endsWith("\r\n") && !quit);
		return input;
	}

	// public static void main(String[] arg)
	// {
	// new Console(); // create console with not reference
	// }
}
