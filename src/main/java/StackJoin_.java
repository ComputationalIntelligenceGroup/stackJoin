import ij.plugin.PlugIn;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Toolbar;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import ij.io.OpenDialog;
import java.awt.Color;

public class StackJoin_ implements PlugIn {

	private int[] coord;
	private int fs;
	private int ls;
	private double scale = 1;
	private String options;
	private String command;
	private String fileNameStart;
	private String filePathStart;
	private double progress = 0;

	public void run(String arg) {
		if (!showDialog()) {
			return;
		}

		ImagePlus tempImage = IJ.openImage(this.filePathStart);

		if (tempImage == null) {
			IJ.error("No Image");
			IJ.showProgress(1);
		} else {
			tempImage = scaleIm(reduceSlices(tempImage, fs, ls), scale);
			if (!this.command.equals("none")) {
				IJ.run(tempImage, this.command, this.options);
			}

			this.coord = findCoord(this.fileNameStart);
			IJ.showProgress(this.progress);
			tempImage = mergingIteratively(this.fileNameStart, this.filePathStart, tempImage);
			tempImage.setTitle("Combined_Stack");
			tempImage.show();
		}
	}

	public int[] findCoord(String fn) {
		int indX = fn.indexOf("X", 0);
		int indY = fn.indexOf("Y", 0);
		String Xs = fn.substring(indX + 1, indY - 1);
		String Ys = fn.substring(indY + 1, fn.indexOf(".tif", indY));
		int[] coord;
		coord = new int[2];
		coord[0] = Integer.parseInt(Xs);
		coord[1] = Integer.parseInt(Ys);
		return coord;
	}

	private ImagePlus reduceSlices(ImagePlus image, int fn, int ls) {
		if (image.getDimensions()[3] == 1) {
			return image;
		} else {
			ImageStack tempStack = new ImageStack(image.getDimensions()[0], image.getDimensions()[1]);
			for (int i = Math.max(fs - 1, 0); i < Math.min(ls, image.getDimensions()[3]); i++) {
				tempStack.addSlice(image.getStack().getProcessor(i + 1));
			}
			ImagePlus img = new ImagePlus("reduced", tempStack);
			return img;
		}
	}

	private ImagePlus scaleIm(ImagePlus image, double scale) {
		if (scale == 1) {
			return image;
		}

		int newwidth = (int) Math.floor((image.getDimensions()[0]) * scale);
		int newlength = (int) Math.floor((image.getDimensions()[1]) * scale);
		ImageStack tempStack = new ImageStack(newwidth, newlength);

		for (int i = 1; i <= image.getDimensions()[3]; i++) {
			tempStack.addSlice(image.getStack().getProcessor(i).resize(newwidth, newlength));
		}
		ImagePlus img = new ImagePlus("reduced", tempStack);
		return img;

	}

	private ImagePlus mergingIteratively(String fn, String fp, ImagePlus imgStart) {
		String tryFile;
		ImageStack stackNew;

		int[] coord = findCoord(fn);
		tryFile = fn.substring(0, fn.indexOf("X", 0) + 1) + Integer.toString(coord[0]) + "_Y"
				+ Integer.toString(coord[1] + 1) + ".tif";
		String fpp = fp.substring(0, fp.indexOf(fn, 0));
		String tryPath = fpp + tryFile;

		ImagePlus imgTry = IJ.openImage(tryPath);

		if (imgTry == null) {
			tryFile = fn.substring(0, fn.indexOf("X", 0) + 1) + Integer.toString(this.coord[0] + 1) + "_Y"
					+ Integer.toString(this.coord[1]) + ".tif";
			tryPath = fpp + tryFile;
			ImagePlus tempImage = IJ.openImage(tryPath);
			if (tempImage == null) {
				IJ.showProgress(1);
				return (imgStart);
			} else {
				tempImage = scaleIm(reduceSlices(tempImage, fs, ls), scale);
				if (!this.command.equals("none")) {
					IJ.run(tempImage, this.command, this.options);
				}
				this.coord[0] = this.coord[0] + 1;
				this.progress = this.progress + 0.1;
				IJ.showProgress(this.progress);
				ImagePlus outImage = mergingIteratively(tryFile, tryPath, tempImage);
				stackNew = combineHorizontallyPlus(imgStart.getStack(), outImage.getStack());
				return (new ImagePlus("Combined Stacks", stackNew));
			}
		} else {
			imgTry = scaleIm(reduceSlices(imgTry, fs, ls), scale);
			if (!this.command.equals("none")) {
				IJ.run(imgTry, this.command, this.options);
			}

			stackNew = combineVerticallyPlus(imgStart.getStack(), imgTry.getStack());
			this.progress = this.progress + 0.02;
			IJ.showProgress(this.progress);
			ImagePlus outImage = new ImagePlus("Combined Stacks", stackNew);
			return mergingIteratively(tryFile, tryPath, outImage);
		}

	}

	public ImageStack combineHorizontallyPlus(ImageStack stack1, ImageStack stack2) {
		int d1 = stack1.getSize();
		int d2 = stack2.getSize();
		int d3 = Math.max(d1, d2);
		int w1 = stack1.getWidth();
		int h1 = stack1.getHeight();
		int w2 = stack2.getWidth();
		int h2 = stack2.getHeight();
		int w3 = w1 + w2;
		int h3 = Math.max(h1, h2);
		ImageStack stack3 = new ImageStack(w3, h3, stack1.getColorModel());
		ImageProcessor ip = stack1.getProcessor(1);

		Color background = Toolbar.getBackgroundColor();
		for (int i = 1; i <= d3; i++) {
			IJ.showProgress(i / d3);
			ImageProcessor ip3 = ip.createProcessor(w3, h3);
			if (h1 != h2) {
				ip3.setColor(background);
				ip3.fill();
			}
			if (i <= d1) {
				ip3.insert(stack1.getProcessor(1), 0, 0);
				if (stack2 != stack1) {
					stack1.deleteSlice(1);
				}
			}
			if (i <= d2) {
				ip3.insert(stack2.getProcessor(1), w1, 0);
				stack2.deleteSlice(1);
			}
			stack3.addSlice(null, ip3);
		}
		return stack3;
	}

	public ImageStack combineVerticallyPlus(ImageStack stack1, ImageStack stack2) {
		int d1 = stack1.getSize();
		int d2 = stack2.getSize();
		int d3 = Math.max(d1, d2);
		int w1 = stack1.getWidth();
		int h1 = stack1.getHeight();
		int w2 = stack2.getWidth();
		int h2 = stack2.getHeight();
		int w3 = Math.max(w1, w2);
		int h3 = h1 + h2;
		ImageStack stack3 = new ImageStack(w3, h3, stack1.getColorModel());
		ImageProcessor ip = stack1.getProcessor(1);

		Color background = Toolbar.getBackgroundColor();
		for (int i = 1; i <= d3; i++) {
			IJ.showProgress(i / d3);
			ImageProcessor ip3 = ip.createProcessor(w3, h3);
			if (w1 != w2) {
				ip3.setColor(background);
				ip3.fill();
			}
			if (i <= d1) {
				ip3.insert(stack1.getProcessor(1), 0, 0);
				if (stack2 != stack1) {
					stack1.deleteSlice(1);
				}
			}
			if (i <= d2) {
				ip3.insert(stack2.getProcessor(1), 0, h1);
				stack2.deleteSlice(1);
			}
			stack3.addSlice(null, ip3);
		}
		return stack3;
	}

	boolean showDialog() {
		OpenDialog dc = new OpenDialog("Image in the left-upper corner");

		this.filePathStart = dc.getPath();
		this.fileNameStart = dc.getFileName();

		if (this.filePathStart == null) {
			return false;
		}

		GenericDialog gd = new GenericDialog("StackCombiner Plus");
		gd.addMessage("The StackCombiner Plus plugin, will now merge various stack to form a bigger image.");
		gd.addStringField("Prior Operations (or none)", "StackReg", 10);
		gd.addStringField("Options", "transformation=[Rigid Body]", 20);
		gd.addStringField("Initial SLice: ", "1", 10);
		gd.addStringField("Last slice: ", "1", 10);
		gd.addNumericField("scale :", 1, 3);
		gd.showDialog();

		if (this.filePathStart == null) {
			return false;
		} else {
			if (gd.wasCanceled()) {

				return false;
			} else {
				this.command = gd.getNextString();
				this.options = gd.getNextString();
				this.fs = Integer.parseInt(gd.getNextString());
				this.ls = Integer.parseInt(gd.getNextString());
				this.scale = gd.getNextNumber();
				return true;
			}

		}
	}

}