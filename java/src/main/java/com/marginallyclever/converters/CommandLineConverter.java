package com.marginallyclever.converters;

import java.io.PrintWriter;

import org.apache.commons.cli.*;

public class CommandLineConverter {
	
	private static CommandLineConverter cmc;
	
	String imageFilename;
	Options options;
	CommandLine commandLine;
	
	public static void main(String[] args) {
		cmc = new CommandLineConverter();
		cmc.init(args);
	}

	private void init(String[] args) {
		buildOptions();
		
		try {
			commandLine = new PosixParser().parse(options, args);
			
			if (args.length == 0) {
				printUsage();
				printHelp();
				return; // skip additional command checks.
			}
			
			if (commandLine.hasOption('h')) {
				printHelp();
				return; // overrides normal operation.
			}
			
			if (commandLine.hasOption("file")) {
				imageFilename = commandLine.getOptionValue("file");
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
	
	private void printUsage() {
		HelpFormatter helpFormatter = new HelpFormatter();
		PrintWriter pw = new PrintWriter(System.out);
		helpFormatter.printUsage(pw, 80, "MakelangeloConverter", options);
		pw.flush();
	}
	
	private void printHelp() {
		HelpFormatter helpFormatter = new HelpFormatter();
		PrintWriter pw = new PrintWriter(System.out);
		helpFormatter.printHelp("java -cp MakelangeloConverter.jar", options);
		pw.flush();
	}
	
	private Options buildOptions() {
		options = new Options();
		Option opt;
		
		// help/usage
		opt = new Option("h", "Print out help (this information).");
		opt.setLongOpt("help");
		options.addOption(opt);
		
		// image file
		opt = new Option("i", true, "Filename of the image to convert.");
		opt.setLongOpt("image");
		opt.setArgName("filename");
		options.addOption(opt);
		
		// converter
		opt = new Option("c", true, "Converter to use on the image.");
		opt.setLongOpt("converter");
		opt.setArgName("converter name");
		options.addOption(opt);
		
		return options;
	}
	
}
