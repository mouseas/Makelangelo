package com.marginallyclever.converters;

import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.marginallyclever.filters.Filter_BlackAndWhite;
import com.marginallyclever.makelangelo.MakelangeloRobotSettings;
import com.marginallyclever.makelangelo.Translator;

public class Converter_Patches extends ImageConverter {
	private static final int DEFAULT_NUM_TONES = 5;
	private static final float DEFAULT_SAMPLE_SIZE = 5.0f;
	private static final int MIN_NUM_TONES = 2;
	
	private static int numTones = DEFAULT_NUM_TONES;
	private static float sampleSize = DEFAULT_SAMPLE_SIZE;
	
	private double xStart, yStart;
	private double xEnd, yEnd;
	private double paperWidth, paperHeight;
	
	public Converter_Patches(MakelangeloRobotSettings mc) {
		super(mc);
	}
	
	@Override
	public String getName() {
		return "Patches";
	}
	
	@Override
	public boolean convert(BufferedImage img,Writer out) throws IOException {
		Filter_BlackAndWhite bw = new Filter_BlackAndWhite(255);
		img = bw.filter(img);
		
		showSettingsPanel(img, out);
		
		return true;
	}
	
	protected void convertToPatches(BufferedImage img, Writer out) throws IOException {
		imageStart(img, out);
		// set absolute coordinates
		out.write("G00 G90;\n");
		tool.writeChangeTo(out);
		liftPen(out);
		
		paperWidth = machine.getPaperWidth();
		paperHeight = machine.getPaperHeight();
		
		// break image into sections of tones
		
		// fill each section with lines, with density proportional to section's tone, and random angle:
		
		liftPen(out);
	}
	
	protected boolean showSettingsPanel(BufferedImage img, Writer out) throws IOException {
		final JTextField field_tones = new JTextField(Integer.toString(numTones));
		final JTextField field_sampleSize = new JTextField(Float.toString(sampleSize));
		
		JPanel panel = new JPanel(new GridLayout(0,1));
		panel.add(new JLabel("Number of Tones (2-25)"));
		panel.add(field_tones);
		
		panel.add(new JLabel("Sample Size"));
		panel.add(field_sampleSize);
		
		int result = JOptionPane.showConfirmDialog(null, panel, getName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.OK_OPTION) {
			// number of tones
			try {
				numTones = Integer.parseInt(field_tones.getText());
			} catch (NumberFormatException e) {
				numTones = DEFAULT_NUM_TONES;
			}
			if (numTones < MIN_NUM_TONES) {
				numTones = MIN_NUM_TONES;
			}
			
			// sample size
			try {
				sampleSize = Float.parseFloat(field_sampleSize.getText());
			} catch (NumberFormatException e) {
				sampleSize = DEFAULT_SAMPLE_SIZE;
			}
			if (sampleSize <= 0) {
				sampleSize = DEFAULT_SAMPLE_SIZE;
			}
			
			// run conversion
			convertToPatches(img, out);
			return true;
		}
		return false;
	}
	
}
