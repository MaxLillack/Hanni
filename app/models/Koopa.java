package models;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import koopa.parsers.ParseResults;
import koopa.parsers.cobol.CobolParser;
import koopa.tokenizers.cobol.SourceFormat;
import koopa.tokens.Token;
import koopa.trees.antlr.CommonTreeSerializer;
import koopa.util.Tuple;

import org.antlr.runtime.tree.CommonTree;

public class Koopa {
	// Wrapper for Koola toXML-Main with use of system.exit()
	
	
	public static void parse(String cobolSourcePath, String outputPath)
	{
		String[] args = {
			cobolSourcePath,
			outputPath
		};
		Koopa.parse(args);
	}
	
	public static void parse(String[] args)
	{
		Properties props = System.getProperties();
		props.setProperty("koopa.xml.include_positioning", "true");
		
		if (args == null || args.length < 2 || args.length > 3) {
			System.out.println("Usage: GetASTAsXML [--free-format] <cobol-input-file> <xml-output-file>");
		}

		SourceFormat format = SourceFormat.FIXED;
		String inputFilename = args[0];
		String outputFilename = args[1];

		if (args.length == 3) {
			String option = args[0];
			if (option.equals("--free-format")) {
				format = SourceFormat.FREE;

			} else {
				System.out.println("Unknown option: " + option);
			}

			inputFilename = args[1];
			outputFilename = args[2];
		}

		final File cobolFile = new File(inputFilename);
		if (!cobolFile.exists()) {
			System.out.println("Input file does not exist: " + cobolFile);
		}

		final CobolParser parser = new CobolParser();
		parser.setFormat(format);
		parser.setBuildTrees(true);

		ParseResults results = null;

		try {
			results = parser.parse(cobolFile);

		} catch (IOException e) {
			System.out.println("IOException while reading " + cobolFile);
		}

		if (results.getErrorCount() > 0) {
			for (int i = 0; i < results.getErrorCount(); i++) {
				final Tuple<Token, String> error = results.getError(i);
				System.out.println("Error: " + error.getFirst() + " "
						+ error.getSecond());
			}
		}

		if (results.getWarningCount() > 0) {
			for (int i = 0; i < results.getWarningCount(); i++) {
				final Tuple<Token, String> warning = results.getWarning(i);
				System.out.println("Warning: " + warning.getFirst() + " "
						+ warning.getSecond());
			}
		}

		if (!results.isValidInput()) {
			System.out.println("Could not parse " + cobolFile);
		}

		final CommonTree ast = results.getTree();

		if(ast != null) {
			final File xmlFile = new File(outputFilename);
			try {
				CommonTreeSerializer.serialize(ast, xmlFile);
	
			} catch (IOException e) {
				System.out.println("IOException while writing " + xmlFile);
			}
		}
	}

}
