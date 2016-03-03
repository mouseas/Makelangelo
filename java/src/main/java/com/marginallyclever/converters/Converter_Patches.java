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

import com.marginallyclever.basictypes.Point2D;
import com.marginallyclever.filters.Filter_BlackAndWhite;
import com.marginallyclever.makelangelo.Log;
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
		
		Log.write("#FFFFFF", "Sampling image tones...");
		sampleImageTones(img);
		
		// break image into sections of tones
		Log.write("#FFFFFF", "Patchifying...");
		List<Patch> patches = patchifySamples();
		
		// fill each section with lines, with density proportional to section's tone, and random angle:
		try {
			Log.write("#FFFFFF", "Drawing patches...");
			for (Patch patch : patches) {
				drawPatch(patch, out);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		liftPen(out);
		Log.write("#FFFFFF", "Done.");
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
			Log.write("#FFFFFF", "Converting to patches...");
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
			Log.write("#FFFFFF", "Tones: " + numTones);
			
			// sample size
			try {
				sampleSize = Float.parseFloat(field_sampleSize.getText());
			} catch (NumberFormatException e) {
				sampleSize = DEFAULT_SAMPLE_SIZE;
			}
			if (sampleSize <= 0) {
				sampleSize = DEFAULT_SAMPLE_SIZE;
			}
			Log.write("#FFFFFF", "Sample size: " + sampleSize + "px");
			
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
		// TODO fix this method. Sometimes the method just dies, possibly due to using too much memory...?
		int firstCellX = 0;
		int firstCellY = 0;
		List<Patch> results = new ArrayList<Patch>();
		short tone;
		// find first unassigned cell for each patch
		for(; firstCellY < numYSamples; firstCellY++) {
			for (firstCellX = 0; firstCellX < numXSamples; firstCellX++) {
				tone = imageField[firstCellY][firstCellX];
				if (tone != ASSIGNED_TONE) {
					// then flood fill, make patch
					results.add(makePatchViaFloodFill(firstCellX, firstCellY, tone));
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
	
	protected void drawPatch(Patch patch, Writer out) throws Exception {
		// get patch bounding box
		double left = patch.xOffset;
		double right = patch.xOffset + patch.width;
		double bottom = patch.yOffset + patch.height;
		
		// determine if patch angle is a special case (vertical or horizontal)
		boolean vertical = patch.lineAngle == 0;
		
		if (!vertical) {
			// calculate the y step which will yield 'distanceBetweenLines'
			double distanceBetweenLines = ((patch.tone * numTones) + 1) * tool.getDiameter();
			// soh cah toa -> sin(angle) = opposite / hypotenuse -> h = o/sin(angle)
			double yStep = Math.abs(distanceBetweenLines / Math.sin(patch.lineAngle));
			
			// calculate lowest y-intercept of lines hitting the bounding box corners. Since one of
			// the bottom corners will always be the lowest y-intercept, we only need to check those two.
			double lowYIntercept = calculateYInterceptForCorner(new Point2D(bottom, left), patch.lineAngle);
			double temp = calculateYInterceptForCorner(new Point2D(bottom, right), patch.lineAngle);
			if (temp < lowYIntercept) {
				lowYIntercept = temp;
			}
			
			// the highest y-intercept is equal to the lowest plus the height of the patch.
			double highYIntercept = lowYIntercept + patch.height;
			
			// build lines for the outline of the patch
			List<Line> lines = new ArrayList<Line>();
			double slope = Math.tan(patch.lineAngle);
			for (double yInt = lowYIntercept; yInt < highYIntercept; yInt += yStep) {
				// TODO figure out line from y intercept to past the right side of patch
				// y = m*x + b -> y = slope * x + yInt
				
			}
		} else {
			// TODO handle special case for perfectly vertical lines
			throw new Exception();
		}
		
		// generate lines for the patch's outline
		List<Line> outline = generatePatchOutline(patch);
		Point2D center = new Point2D(-(numXSamples / 2), -(numYSamples / 2));
		Point2D flipVertical = new Point2D(1, -1);
		for (Line line : outline) {
			translateLine(line, center);
			scaleLine(line, flipVertical);
			drawLine(line, out);
		}
		
		// check where lines cross the patch outline. These points should occur in pairs.
		
		// create Lines for each pair
		
		// scale for the physical size of one sample, and transform so center is (0,0)
		//   ^ might also need to invert y, since image (1,1) is probably physical (1*scale,-1*scale)
		
		// draw lines, lifting between lines
		
		// TODO optimize line drawing to minimize distance moved between lines.
		// TODO when distance is less than 'distanceBetweenLines * 1.5' or so, don't lift pen.
	}
	
	protected double calculateYInterceptForCorner(Point2D corner, double angle) {
		// soh cah toa -> tan(angle) = opposite / adjacent -> a = o/tan(angle)
		return (corner.x / Math.tan(angle)) + corner.y;
	}
	
	protected List<Line> generatePatchOutline(Patch patch) {
		List<Line> result = new ArrayList<Line>();
		
		// vertical lines
		for (int x = 0; x <= patch.width; x++) {
			int startY = -1;
			for (int y = 0; y <= patch.height; y++) {
				if (patch.getValueAt(x - 1, y) != patch.getValueAt(x, y)) {
					// line should include (x,y) to (x,y+1)
					if (startY < 0) {
						startY = y;
					}
				} else {
					// line should not include (x,y) to (x,y+1).
					// end + save any current line
					if (startY >= 0) {
						result.add(new Line(x + patch.xOffset, startY + patch.yOffset,
								x + patch.xOffset, y + patch.yOffset));
					}
					startY = -1;
				}
			}
		}
		
		// horizontal lines
		for (int y = 0; y <= patch.height; y++) {
			int startX = -1;
			for (int x = 0; x <= patch.width; x++) {
				if (patch.getValueAt(x, y - 1) != patch.getValueAt(x, y)) {
					// line should include (x,y) to (x+1,y)
					if (startX < 0) {
						// mark start of line
						startX = x;
					}
				} else {
					// line should not include (x,y) to (x+1,y).
					if (startX >= 0) {
						// end + save any current line
						result.add(new Line(startX + patch.xOffset, y + patch.yOffset,
								x + patch.xOffset, y + patch.yOffset));
					}
					startX = -1;
				}
			}
		}
		
		return result;
	}
	
	protected void scaleLine(Line line, Point2D scale) {
		line.a.x = scale.x * line.a.x;
		line.a.y = scale.y * line.a.y;
		line.b.x = scale.x * line.b.x;
		line.b.y = scale.y * line.b.y;
	}
	
	protected void translateLine(Line line, Point2D translation) {
		line.a.x += translation.x;
		line.a.y += translation.y;
		line.b.x += translation.x;
		line.b.y += translation.y;
	}
	
	protected void drawLine(Line line, Writer out) {
		try {
			// go to point a without drawing
			if (!lastUp) { // TODO don't lift if distance to a is (close to) 0
				liftPen(out);
			}
			tool.writeMoveTo(out, (float) line.a.x, (float) line.a.y);
			
			// draw from a to b.
			if (lastUp) {
				lowerPen(out);
			}
			tool.writeMoveTo(out, (float) line.b.x, (float) line.b.y);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
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
			
			lineAngle = (rand.nextDouble() * Math.PI) - (Math.PI / 2);
			// angles range from straight up, to the right, to straight down, ie left to right
			
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
		}
		
		/*@Override
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
		}*/
		
		public boolean getValueAt(int x, int y) {
			if (x < 0 || x >= width || y < 0 || y >= height) {
				return false;
			}
			return samples[y][x];
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
	
	protected class Line {
		protected Point2D a, b;
		
		public Line(double x1, double y1, double x2, double y2) {
			this.a = new Point2D(x1, y1);
			this.b = new Point2D(x2, y2);
		}
		
		public Line(Point2D a, Point2D b) {
			this.a = a;
			this.b = b;
		}
		
		@Override
		public String toString() {
			return "(" + a.x + "," + a.y + ") to (" + b.x + "," + b.y + ")";
		}
	}
}
