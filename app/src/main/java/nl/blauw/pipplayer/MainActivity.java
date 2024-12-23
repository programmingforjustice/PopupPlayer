package nl.blauw.pipplayer;


import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.TargetApi;

import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Typeface;

import android.view.*;
import java.io.*;

import java.util.*;
import android.os.*;

import android.provider.Settings;

public class MainActivity extends AppCompatActivity {

    public final static int REQUEST_CODE = 100;

    private Button playButton;
	
	  private Button playListButton;
    
    private Button exitButton;

    private EditText urlEdit;
    
    private List<String> urlList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

      	urlEdit = (EditText)findViewById(R.id.edit_url);
      	playButton = (Button)findViewById(R.id.button_play);
      	playButton.setOnClickListener(new View.OnClickListener() { 
                  @Override
                  public void onClick(View view) 
                  { 
                      String url = urlEdit.getText().toString();
					  if (url == null || url.trim().length() == 0) 
					  	return;
						  
      		        startPipPlayer(url);
      		        // addTextView(url);
      		        writeUrl(url);
      			    urlEdit.setText("");
                  } 
              }); 
			  
		playListButton = (Button)findViewById(R.id.button_playlist);
        playListButton.setOnClickListener(new View.OnClickListener() { 
            @Override
            public void onClick(View view) 
            { 
				Intent intent = new Intent(MainActivity.this, PlayerListActivity.class);
            	startActivity(intent);
            } 
        }); 
              
        exitButton = (Button)findViewById(R.id.button_exit);
      	exitButton.setOnClickListener(new View.OnClickListener() { 
                  @Override
                  public void onClick(View view) 
                  { 
      		          MainActivity.this.finish();
                    System.exit(0);
                  } 
              }); 
        
        // for (String url: readUrlList()) {
        //   addTextView(url);
        // }
        
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 파일 읽어서 리스트에 추가
        List<String> lines = readUrlList();

        // RecyclerView에 어댑터 연결
        TextAdapter adapter = new TextAdapter(lines);
        recyclerView.setAdapter(adapter);

	if (Build.VERSION.SDK_INT >= 30){
		if (!Environment.isExternalStorageManager()){
		    Intent getpermission = new Intent();
		    getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
		    startActivity(getpermission);
		}
	    }
    }
    
    private void addTextView(String url) {
      LinearLayout layout = (LinearLayout)findViewById(R.id.layout_main);
      
      TextView urlText = new TextView(this);

    urlText.setLayoutParams(new LinearLayout.LayoutParams(
        LayoutParams.FILL_PARENT,
        LayoutParams.WRAP_CONTENT));

    urlText.setTextSize(12);
    urlText.setPadding(2, 2, 2, 2);
    urlText.setTypeface(Typeface.DEFAULT_BOLD);
    urlText.setTextColor(getResources().getColor(R.color.lightBlue));
    urlText.setGravity(Gravity.LEFT | Gravity.CENTER);
    urlText.setText(url);

    urlText.setOnClickListener(new View.OnClickListener() { 
            @Override
            public void onClick(View view) 
            { 
                String url = ((TextView)view).getText().toString();
				if (url != null && !"nothing".equalsIgnoreCase(url)) {
		        	startPipPlayer(url);
				}
            } 
        }); 
      
    
   	 layout.addView(urlText);
    }
    
    private void writeUrl(String url) {
      PrintWriter writer = null;
      try {
        writer = new PrintWriter(new OutputStreamWriter(
        openFileOutput("playlist.content", Context.MODE_APPEND)), true);
        writer.println(url);
      } catch (IOException ioe) {
        ioe.printStackTrace();
      } finally {
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
      }
    }

    private List<String> readUrlList() {
      File file = this.getFileStreamPath("playlist.content");
      if(file == null || !file.exists()) {  
         return Collections.emptyList(); 
      } 

      List<String> urlList = new ArrayList<>();
      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new InputStreamReader(
        openFileInput("playlist.content")));
        String url = null;
        while ((url = reader.readLine()) != null) {
          urlList.add(url);
        }
      } catch (IOException ioe) {
        ioe.printStackTrace();
      } finally {
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
      }
      return urlList;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startPipPlayer(String url) {
        if (!Settings.canDrawOverlays(this)) {
            Intent request = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + this.getPackageName()));
            startActivityForResult(request, REQUEST_CODE);
        } else {
            Intent intent = new Intent(this, PlayerService.class);
            intent.putExtra("data", url);
            startService(intent);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	super.onActivityResult(requestCode, resultCode, data);
        //check if received result code
        //is equal our requested code for draw permission
        if (requestCode == REQUEST_CODE) {
            startPipPlayer(urlEdit.getText().toString());
        }
    }
}
