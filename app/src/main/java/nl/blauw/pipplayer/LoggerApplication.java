package nl.blauw.pipplayer;

import android.app.Application;
public class LoggerApplication extends Application {
   public void onCreate() {
       super.onCreate();
		Logger.initialize(this);
   }
}