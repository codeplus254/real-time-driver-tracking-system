package application;

import java.awt.Toolkit; 
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream; 
import java.io.File;
import java.nio.ByteBuffer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.Timer;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;
import org.opencv.videoio.VideoCapture;



public class ControllerDTS {
	// the camera button
	@FXML
	private Button cam;
	// the area for showing the video
	@FXML
	private ImageView frame;
	
	// a timer for acquiring the video stream
	private ScheduledExecutorService timer;
	// the OpenCV object that performs the video capture
	private VideoCapture capture;
	// a flag to change the button behavior
	private boolean cameraActive;
	private boolean rect1=false;
	private boolean rect2=false; 
	// face cascade classifier
	private CascadeClassifier faceCascade;
	private CascadeClassifier eyeCascade;
	private int absoluteFaceSize;
	private int maxEyeSize;	
	private int minEyeSize;
	private int i;
	Alert alert = new Alert(AlertType.WARNING);
	
	
	//private static final AudioClip ALERT_AUDIOCLIP = new AudioClip(ControllerDTS.class.getResource("/alert.wav").toString());
	
	// Initialize the DTScontroller
	
	protected void init()
	{
		this.capture = new VideoCapture();
		this.faceCascade = new CascadeClassifier();
		this.eyeCascade=new CascadeClassifier();
		this.absoluteFaceSize = 0;
		this.maxEyeSize = 0;
		this.minEyeSize = 0;
		loadCascade();
		
	}
	/**
	 * The action triggered by pushing the button on the GUI
	 */
	@FXML
	protected void start_cam()
	{
		// set a fixed width for the frame
		frame.setFitWidth(600);
		// preserve image ratio
		frame.setPreserveRatio(true);
		Toolkit.getDefaultToolkit().beep();
		
		if (!this.cameraActive)
		{
			
			// start the video capture
			this.capture.open(0);
			
			// check if the video stream is available
			if (this.capture.isOpened())
			{
				this.cameraActive = true;
				
				// grab a frame every 33 ms (30 frames/sec)
				Runnable frameGrabber = new Runnable() {
					
					@Override
					public void run()
					{
						Image imageToShow = grabFrame();
						frame.setImage(imageToShow);
					}
				};
				
				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
				
				// change the button from start to stop once clicked
				this.cam.setText("Stop Camera");
			}
			else
			{
				
				System.err.println("Failed to open the camera connection...");
			}
		}
		else
		{
			// the camera is not active at this point
			this.cameraActive = false;
			// update again the button content
			this.cam.setText("Start Camera");
			
			
			// stop the timer
			try
			{
				this.timer.shutdown();
				this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException e)
			{
				System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
			}
			
			// release the camera
			this.capture.release();
			// clean the frame
			this.frame.setImage(null);
		}
		
	}
	
	//Get a frame from the opened video stream (if any)
	
	private Image grabFrame()
	{
		// Everything is now being intialized
		Image imageToShow = null;
		Mat OpenCVframe = new Mat();
		
		// check if the capture is open
		if (this.capture.isOpened())
		{
			try
			{
				// read the current frame
				this.capture.read(OpenCVframe);
				
				// if the frame is not empty, process it
				if (!OpenCVframe.empty())
				{
					// eyes and face detection
					rect1=false;
					rect2=false;
					this.detectAndDisplay(OpenCVframe);
					
					// convert the Mat object (OpenCV) to Image (JavaFX)
					imageToShow = mat2Image(OpenCVframe);
					
					
					alarm();
				}
				
				
			}
			catch (Exception e)
			{
				// log the (full) error
				System.err.println("ERROR: " + e);
			}
		}
		
		return imageToShow;
	}
	/**
	 * Method for face detection and tracking
	 *            it looks for faces in this frame
	 */
	private void detectAndDisplay(Mat OpenCVframe)
	{
		MatOfRect faces = new MatOfRect();
		MatOfRect eyes = new MatOfRect();
		Mat grayFrame = new Mat();
		
		// convert the frame in gray scale
		Imgproc.cvtColor(OpenCVframe, grayFrame, Imgproc.COLOR_BGR2GRAY);
		// equalize the frame histogram to improve the result
		Imgproc.equalizeHist(grayFrame, grayFrame);
		
		// compute minimum face size (20% of the frame height, in our case)
		if (this.absoluteFaceSize == 0)
		{
			int height = grayFrame.rows();
			if (Math.round(height * 0.2f) > 0)
			{
				this.absoluteFaceSize = Math.round(height * 0.2f);
			}
		}
		// compute maximum eye size (8% of the frame height, in our case)
		if (this.maxEyeSize == 0)
		{
			int height = grayFrame.rows();
			if (Math.round(height * 0.08f) < 0)
			{
				this.maxEyeSize = Math.round(height * 0.08f);
			}
		}
		// compute minimum eye size (10% of the frame height, in our case)
		if (this.minEyeSize == 0)
		{
			int height = grayFrame.rows();
			if (Math.round(height * 0.1f) > 0)
			{
				this.minEyeSize = Math.round(height * 0.1f);
			}
		}
		// detect faces
		this.faceCascade.detectMultiScale(grayFrame, faces, 1.1, 2, 0 | Objdetect.CASCADE_SCALE_IMAGE,
				new Size(this.absoluteFaceSize, this.absoluteFaceSize), new Size());
		this.eyeCascade.detectMultiScale(grayFrame, eyes, 1.1, 2, 1 | Objdetect.CASCADE_SCALE_IMAGE,
				new Size(this.minEyeSize, this.minEyeSize),new Size() );		
		// draw rectangles!
		Rect[] facesArray = faces.toArray();
		Rect[] eyesArray = eyes.toArray();
		for (int i = 0; i < facesArray.length; i++){
			Imgproc.rectangle(OpenCVframe, facesArray[i].tl(), facesArray[i].br(), new Scalar(255, 255, 0), 3);
			rect1= true; 
		}
		for (int i = 0; i < eyesArray.length; i++){	
			Imgproc.rectangle(OpenCVframe, eyesArray[i].tl(), eyesArray[i].br(), new Scalar(255, 255, 0), 1);
			rect2=true;
		}
		//alarm();
		
	}
	/**
	 * this function is responsible for the beep sound and the alert box
	 */
	private void alarm(){
		
		if (rect1==false && rect2==false){
			// when i is 25, 5 seconds will have elapsed
			if (i==25){
				try {
					beep(750, 3000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (LineUnavailableException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				alert.setContentText("Please concentrate!");
				Platform.runLater(new Runnable(){
				    @Override
				    public void run() {
				    	if (alert.isShowing()==false){
				    	alert.showAndWait();
				    	}
				    }
					
					});
				
				
			}
			//count consecutive loss of focus
			else {
			i++;
			}
		}
		else if (rect1==true && rect2==true) {
			//rest the counter i back to zero since focus has been regained
			i=0;
			Platform.runLater(new Runnable(){
			    @Override
			    public void run() {
			    	if (alert.isShowing()==true){
			    		alert.hide();
			    	}
			    }
				
			});
		}
	
		
	
            
	}
	
	/**
	 * Method for loading a classifier trained set from disk
	 * 
	 * @param classifierPath
	 *            the path on disk where a classifier trained set is located
	 */
	private void loadCascade()
	{
		// load the classifier(s)
		this.faceCascade.load("CascadeHaar/haarcascade_frontalface_alt.xml");
		this.eyeCascade.load("CascadeHaar/frontalEyes35x16.xml");
		
		// now the video capture can start
		this.cam.setDisable(false);
	}
	/**
	 * Convert a Mat object (OpenCV) in the corresponding Image for JavaFX
	 * 
	 * @param frame
	 *            the {@link Mat} representing the current frame
	 * @return the {@link Image} to show
	 */
	private Image mat2Image(Mat OpenCVframe)
	{
		// create a temporary buffer
		MatOfByte buffer = new MatOfByte();
		// encode the frame in the buffer, according to the PNG format
		Imgcodecs.imencode(".png", OpenCVframe, buffer);
		// build and return an Image created from the image encoded in the
		// buffer
		return new Image(new ByteArrayInputStream(buffer.toArray()));
	}
	
    public static void beep(double freq, final double millis) throws InterruptedException, LineUnavailableException {

        final Clip clip = AudioSystem.getClip();
        /**
         * AudioFormat of the reclieved clip. Probably you can alter it
         * someway choosing proper Line.
         */
        AudioFormat af = clip.getFormat();

        /**
         * We assume that encoding uses signed shorts. Probably we could
         * make this code more generic but who cares.
         */
        if (af.getEncoding() != AudioFormat.Encoding.PCM_SIGNED){
            throw new UnsupportedOperationException("Unknown encoding");
        }

        if (af.getSampleSizeInBits() != 16) {
            System.err.println("Weird sample size.  Dunno what to do with it.");
            return;
        }

        /**
         * Number of bytes in a single frame
         */
        int bytesPerFrame = af.getFrameSize();
        /**
         * Number of frames per second
         */
        double fps = af.getFrameRate();
        /**
         * Number of frames during the clip .
         */
        int frames = (int)(fps * (millis / 1000));

        /**
         * Data
         */
        ByteBuffer data = ByteBuffer.allocate(frames * bytesPerFrame);

        /**
         * We will emit sinus, which needs to be scaled so it has proper
         * frequency --- here is the scaling factor.
         */
        double freqFactor = (Math.PI / 2) * freq / fps;
        /**
         * This sinus must also be scaled so it fills short.
         */
        double ampFactor = Short.MAX_VALUE;

        short sample;

        for (int frame = 0; frame < frames; frame++) {
            sample = (short) (ampFactor * Math.sin(frame * freqFactor));
            data.putShort(sample);
        }
        clip.open(af, data.array(), 0, data.position());

        // This is so Clip releases its data line when done.  Otherwise at 32 clips it breaks.
        clip.addLineListener(new LineListener() {
            @Override
            public void update(LineEvent event) {
                if (event.getType() == LineEvent.Type.START) {
                    Timer t = new Timer((int)millis + 1, new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            clip.close();
                        }
                    });
                    t.setRepeats(false);
                    t.start();
                }
            }
        });
        clip.start();

        Thread.sleep((long)millis);



    }

}
