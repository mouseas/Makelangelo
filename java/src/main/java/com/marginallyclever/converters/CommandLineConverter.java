package com.marginallyclever.converters;

import org.apache.commons.cli.*;

public class CommandLineConverter {
	
	private static CommandLineConverter cmc;
	
	String imageFilename;
	
	public static void main(String[] args) {
		cmc = new CommandLineConverter();
		cmc.init(args);
	}

	private void init(String[] args) {
		Options options = new Options();
		options.addOption("image", false, "Filename of the image to convert.");
		CommandLine commandLine;
		try {
			commandLine = new PosixParser().parse(options, args);
			if (commandLine.hasOption("file")) {
				imageFilename = commandLine.getOptionValue("file");
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
	
	
	
}
