import com.googlecode.javacv.*;
import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import static com.googlecode.javacv.cpp.opencv_highgui.*;
import static com.googlecode.javacv.cpp.opencv_video.*;

public class LucasKanadeJavaCV {
  private static final String APP_NAME = "LucasKanadeJavaCV";
  private static final String OUTPUT_FILE_NAME = "output.avi";
  private static final int FOURCC = CV_FOURCC('X', 'V', 'I', 'D');
  private static final int MAX_CORNERS = 500;
  private static final int WINDOW_SIZE = 10;

  private static FFmpegFrameGrabber grabber;
  private static IplImage currentFrame;
  private static IplImage[] frameBuffer;

  private static boolean queryFrame() throws Exception {
    IplImage frame = grabber.grab();

    if (frame != null) {
      cvConvertImage(frame, currentFrame, 0);

      IplImage temp = frameBuffer[0];
      frameBuffer[0] = frameBuffer[1];
      frameBuffer[1] = temp;
      cvConvertImage(frame, frameBuffer[0], 0);

      return true;
    }
    else {
      return false;
    }
  }

  public static void main(String[] args) throws Exception {
    // Check parameters
    if (args.length < 1) {
      System.err.printf("%s: %s\n", APP_NAME, "No video name given");
      System.err.printf("Usage: java %s <video file name> [output file name]\n", APP_NAME);

      System.exit(1);
    }

    String outputFileName;
    if (args.length == 2) {
      outputFileName = args[1];
    }
    else
    {
      outputFileName = OUTPUT_FILE_NAME;
    }

    // Load video
    String fileName = args[0];
    grabber = new FFmpegFrameGrabber(fileName);
    grabber.start();


    // Extract video parameters
    CvSize frameSize = new CvSize(grabber.getImageWidth(), grabber.getImageHeight());

    // Initialize video writer
    OpenCVFrameRecorder recorder =
      new OpenCVFrameRecorder(outputFileName, frameSize.width(), frameSize.height());
    recorder.setCodecID(FOURCC);
    recorder.setFrameRate(grabber.getFrameRate());
    recorder.start();

    // Initialize variables for optical flow calculation
    currentFrame = cvCreateImage(frameSize, IPL_DEPTH_8U, 3);
    IplImage eigenImage = cvCreateImage(frameSize, IPL_DEPTH_32F, 1);
    IplImage tempImage = cvCreateImage(frameSize, IPL_DEPTH_32F, 1);

    int[] cornerCount = {MAX_CORNERS};
    byte[] featuresFound = new byte[MAX_CORNERS];
    float[] featureErrors = new float[MAX_CORNERS];

    IplImage[] pyramidImages = new IplImage[2];
    frameBuffer = new IplImage[2];

    for (int i = 0; i < 2; i++) {
      frameBuffer[i] = cvCreateImage(frameSize, IPL_DEPTH_8U, 1);
      pyramidImages[i] = cvCreateImage(frameSize, IPL_DEPTH_32F, 1);
    }

    // Process video
    while (queryFrame()) {
      CvPoint2D32f corners1 = new CvPoint2D32f(MAX_CORNERS);
      CvPoint2D32f corners2 = new CvPoint2D32f(MAX_CORNERS);

      // Corner finding with Shi and Thomasi
      cvGoodFeaturesToTrack(
        frameBuffer[0],
        eigenImage,
        tempImage,
        corners1,
        cornerCount,
        0.01,
        5.0,
        null,
        3,
        0,
        0.4);

      cvFindCornerSubPix(
        frameBuffer[0],
        corners1,
        cornerCount[0],
        cvSize(WINDOW_SIZE, WINDOW_SIZE),
        cvSize(-1, -1),
        cvTermCriteria(CV_TERMCRIT_ITER | CV_TERMCRIT_EPS, 20, 0.3));

      // Pyramid Lucas-Kanade
      cvCalcOpticalFlowPyrLK(
        frameBuffer[0],
        frameBuffer[1],
        pyramidImages[0],
        pyramidImages[1],
        corners1,
        corners2,
        cornerCount[0],
        cvSize(WINDOW_SIZE, WINDOW_SIZE),
        5,
        featuresFound,
        featureErrors,
        cvTermCriteria(CV_TERMCRIT_ITER | CV_TERMCRIT_EPS, 20, 0.3),
        0);

      // Draw optical flow vectors
      for (int i = 0; i < cornerCount[0]; i++) {
        if (featuresFound[i] == 0 || featureErrors[i] > 550) {
          continue;
        }

        corners1.position(i);
        corners2.position(i);
        CvPoint point1 = cvPoint(Math.round(corners1.x()), Math.round(corners1.y()));
        CvPoint point2 = cvPoint(Math.round(corners2.x()), Math.round(corners2.y()));
        cvLine(currentFrame, point1, point2, CV_RGB(255, 0, 0), 1, 8, 0);
      }

      recorder.record(currentFrame);
    }

    grabber.stop();
    recorder.stop();
  }
}
