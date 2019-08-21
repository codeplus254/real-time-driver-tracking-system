package application;
	
import java.awt.Toolkit;

import javafx.application.Application; 
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import org.opencv.core.Core; 

import javafx.fxml.FXMLLoader;


public class Main extends Application {
	@Override
	public void start(Stage primaryStage) {
		try {
			// load the FXML resource
			FXMLLoader loader = new FXMLLoader(getClass().getResource("DTS.fxml"));
			BorderPane root = (BorderPane) loader.load();
			
			Scene scene = new Scene(root,800,600);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			//Setting title
			primaryStage.setTitle("Real Time Driver Tracking System"); 
			primaryStage.setScene(scene);
			primaryStage.show();
			
			// Initializes the controller
			ControllerDTS controller = loader.getController();
			controller.init();
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		// load the native OpenCV library
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
				
		launch(args);
		Toolkit.getDefaultToolkit().beep();
		Toolkit.getDefaultToolkit().beep();
		Toolkit.getDefaultToolkit().beep();
	}
}
