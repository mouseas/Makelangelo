package com.marginallyclever.converters;

import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.marginallyclever.filters.Filter_BlackAndWhite;
import com.marginallyclever.makelangelo.MakelangeloRobotSettings;
import com.marginallyclever.makelangelo.Translator;

public class Converter_Patches extends ImageConverter {
	private static final short DEFAULT_NUM_TONES = 5;
	private static final float DEFAULT_SAMPLE_SIZE = 5.0f;
	private static final short MIN_NUM_TONES = 2;
	private static final short MAX_NUM_TONES = 25;
	
	private static final short ASSIGNED_TONE = -1;
	private static final short FILLING_TONE = -2;
	
	private static short numTones = DEFAULT_NUM_TONES;
	private static float sampleSize = DEFAULT_SAMPLE_SIZE;
	
	private Random rand = new Random();
	private double paperWidth, paperHeight;
	private int numXSamples, numYSamples;
	private short[][] imageField;
	
	public Converter_Patches(MakelangeloRobotSettings mc) {
		super(mc);
	}
	
	@Override
	public String getName() {
		return "Patches";
	}
	
	@Override
	public boolean convert(BufferedImage img, Writer out) throws IOException {
		Filter_BlackAndWhite bw = new Filter_BlackAndWhite(255);
		img = bw.filter(img);
		
		showSettingsPanel(img, out);
		
		return true;
	}
	
	protected void convertImageToSketchyPatches(BufferedImage img, Writer out) throws IOException {
		imageStart(img, out);
		// set absolute coordinates
		out.write("G00 G90;\n");
		tool.writeChangeTo(out);
		liftPen(out);
		
		paperWidth = machine.getPaperWidth();
		paperHeight = machine.getPaperHeight();
		
		// sample the image to get the tones of the image
		numXSamples = (int) (img.getWidth() / sampleSize);
		numYSamples = (int) (img.getHeight() / sampleSize);
		
		sampleImageTones(img);
		
		// break image into sections of tones
		List<Patch> patches = patchifySamples();
		
		// fill each section with lines, with density proportional to section's tone, and random angle:
		for (Patch patch : patches) {
			drawPatch(patch, out);
		}
		
		liftPen(out);
	}
	
	protected boolean showSettingsPanel(BufferedImage img, Writer out) throws IOException {
		final JTextField field_tones = new JTextField(Integer.toString(numTones));
		final JTextField field_sampleSize = new JTextField(Float.toString(sampleSize));
		
		JPanel panel = new JPanel(new GridLayout(0,1));
		panel.add(new JLabel("Number of Tones (2-25)"));
		panel.add(field_tones);
		
		panel.add(new JLabel("Sample Size (in pixels)"));
		panel.add(field_sampleSize);
		
		int result = JOptionPane.showConfirmDialog(null, panel, getName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.OK_OPTION) {
			// number of tones
			try {
				numTones = Short.parseShort(field_tones.getText());
			} catch (NumberFormatException e) {
				numTones = DEFAULT_NUM_TONES;
			}
			if (numTones < MIN_NUM_TONES) {
				numTones = MIN_NUM_TONES;
			}
			if (numTones > MAX_NUM_TONES) {
				numTones = MAX_NUM_TONES;
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
			convertImageToSketchyPatches(img, out);
			return true;
		}
		return false;
	}
	
	protected void sampleImageTones(BufferedImage img) {
		imageField = new short[numYSamples][numXSamples];
		for (int y = 0; y < numYSamples; y++) {
			for (int x = 0; x < numXSamples; x++) {
				// get sample value (black-white)
				int value = sample(img, x * sampleSize, y * sampleSize,
						(x + 1) * sampleSize, (y + 1) * sampleSize);
				// convert it to tones
				short tone = (short) ((value * numTones) / 256);
				imageField[y][x] = tone;
			}
		}
	}
	
	protected List<Patch> patchifySamples() {
		int numUnassignedCells = numXSamples * numYSamples;
		int firstCellX = 0;
		int firstCellY = 0;
		List<Patch> results = new ArrayList<Patch>();
		while (numUnassignedCells > 0) {
			// find first unassigned cell
			short tone;
			for(; firstCellY < numYSamples; firstCellY++) {
				for (firstCellX = 0; firstCellX < numXSamples; firstCellX++) {
					tone = imageField[firstCellY][firstCellX];
					if (tone != ASSIGNED_TONE) {
						// then flood fill, make patch
						results.add(makePatchViaFloodFill(firstCellX, firstCellY, tone));
					}
				}
			}
		}
		return results;
	}
	
	protected Converter_Patches.Patch makePatchViaFloodFill(int firstCellX, int firstCellY, short tone) {
		// flood fill to find all cells for this patch
		floodFill(firstCellX, firstCellY, tone);
		// find the bounds of this patch
		int lowX = numXSamples, lowY = numYSamples, highX = 0, highY = 0;
		for (int y = 0; y < numYSamples; y++) {
			for (int x = 0; x < numXSamples; x++) {
				if (imageField[y][x] == FILLING_TONE) {
					if (x < lowX) { lowX = x; }
					if (y < lowY) { lowY = y; }
					if (x > highX) { highX = x; }
					if (y > highY) { highY = y; }
				}
			}
		}
		// create the Patch
		Patch result = new Patch(lowX, lowY, 1 + highX - lowX, 1 + highY - lowY, tone);
		return result;
	}
	
	/**
	 * Recursive flood fill algorithm. If the current cell is the target tone, mark it, then check adjacent cells.
	 */
	protected void floodFill(int x, int y, short tone) {
		if (x < 0 || y < 0 || x >= numXSamples || y >= numYSamples) {
			return;
		}
		if (imageField[y][x] == tone) {
			imageField[y][x] = FILLING_TONE;
//			debugImageField();
			floodFill(x + 1, y, tone);
			floodFill(x - 1, y, tone);
			floodFill(x, y + 1, tone);
			floodFill(x, y - 1, tone);
		}
	}
	
	protected void drawPatch(Patch patch, Writer out) throws IOException {
		double patchPaperXOffset, patchPaperYOffset;
		
		double distanceBetweenLines = ((patch.tone * numTones) + 1) * tool.getDiameter();
		
		
		liftPen(out);
		// go to top-most cell in the left-most column, and start drawing from there
		
	}
	
	/**
	 * Represents a single patch of contiguous samples in the same tone.
	 * @author Martin Carney
	 */
	protected class Patch {
		double lineAngle;
		
		int xOffset, yOffset;
		int width, height;
		short tone;
		
		boolean[][] samples;
		
		public Patch(int xOffset, int yOffset, int width, int height, short tone) {
			this.xOffset = xOffset;
			this.yOffset = yOffset;
			this.width = width;
			this.height = height;
			this.tone = tone;
			
			lineAngle = rand.nextDouble() * 180; // 181-360 are the same as 1-180
			
			// read the imageField to determine which cells belong to this patch, and mark those cells
			// as ASSIGNED.
			samples = new boolean[height][width];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					samples[y][x] = imageField[y + yOffset][x + xOffset] == FILLING_TONE;
					if (samples[y][x]) {
						imageField[y + yOffset][x + xOffset] = ASSIGNED_TONE;
					}
				}
			}
			
			System.out.println(toString());
		}
		
		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();
			for (int y = 0; y < numYSamples; y++) {
				result.append('|');
				for (int x = 0; x < numXSamples; x++) {
					if (x >= xOffset && x < xOffset + width && y >= yOffset && y < yOffset + height) {
						if (samples[y - yOffset][x - xOffset]) {
							result.append('X');
						} else {
							result.append('_');
						}
					} else {
						result.append(' ');
					}
				}
				result.append("|\n");
			}
			return result.toString();
		}
	}
	
	protected void debugImageField() {
		StringBuilder result = new StringBuilder();
		for (int y = 0; y < numYSamples; y++) {
			result.append('|');
			for (int x = 0; x < numXSamples; x++) {
				if (imageField[y][x] == FILLING_TONE) {
					result.append('X');
				} else if (imageField[y][x] == ASSIGNED_TONE) {
					result.append('_');
				} else {
					result.append(imageField[y][x]);
				}
			}
			result.append("|\n");
		}
		System.out.println(result.toString());
	}
}
