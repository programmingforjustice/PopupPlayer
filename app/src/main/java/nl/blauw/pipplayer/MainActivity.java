package nl.blauw.pipplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {

  public static final int REQUEST_CODE_OVERLAY_PERMISSION = 100;

  private TextAdapter adapter;

  private Button playButton;

  private Button playListButton;

  private Button refreshButton;
  
  private Button saveButton;
  
  private Button restoreButton;

  private Button exitButton;

  private EditText urlEdit;

  private List<String> urlList;
  
  private String savedUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    urlEdit = (EditText) findViewById(R.id.edit_url);
    playButton = (Button) findViewById(R.id.button_play);
    playButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            String url = urlEdit.getText().toString();
            if (url == null || url.trim().length() == 0) return;

            startPipPlayer(url);
            // addTextView(url);
            writeUrl(url);
            refreshData();
            urlEdit.setText("");
          }
        });

    playListButton = (Button) findViewById(R.id.button_playlist);
    playListButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            Intent intent = new Intent(MainActivity.this, PlayerListActivity.class);
            startActivity(intent);
          }
        });

    refreshButton = (Button) findViewById(R.id.button_refresh);
    refreshButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            refreshData();
          }
        });
        
    saveButton = (Button) findViewById(R.id.button_save);
    saveButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            //savePlayList();
            Intent intent = new Intent(MainActivity.this, PlayerService.class);
            intent.putExtra(PlayerService.COMMAND, PlayerService.ACTION_SAVE_CURRENT_PLAYLIST);
            startForegroundService(intent);
          }
        });
        
    restoreButton = (Button) findViewById(R.id.button_restore);
    restoreButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            //restorePlayList();
            Intent intent = new Intent(MainActivity.this, PlayerService.class);
            intent.putExtra(PlayerService.COMMAND, PlayerService.ACTION_RESTORE_CURRENT_PLAYLIST);
            startForegroundService(intent);
          }
        });

    exitButton = (Button) findViewById(R.id.button_exit);
    exitButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            Intent intent = new Intent(MainActivity.this, PlayerService.class);
            stopService(intent);
            //MainActivity.this.finish();
            //System.exit(0);
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
    adapter =
        new TextAdapter(
            lines,
            url -> {
              if (url != null && !"nothing".equalsIgnoreCase(url)) {
                startPipPlayer(url);
              }
            });
    recyclerView.setAdapter(adapter);

    if (Build.VERSION.SDK_INT >= 30) {
      if (!Environment.isExternalStorageManager()) {
        Intent getpermission = new Intent();
        getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        startActivity(getpermission);
      }
    }
  }

  private void refreshData() {
    List<String> newLines = readUrlList(); // 새 데이터를 읽음
    adapter.updateData(newLines); // 어댑터 갱신
  }

  private void addTextView(String url) {
    LinearLayout layout = (LinearLayout) findViewById(R.id.layout_main);

    TextView urlText = new TextView(this);

    urlText.setLayoutParams(
        new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

    urlText.setTextSize(12);
    urlText.setPadding(2, 2, 2, 2);
    urlText.setTypeface(Typeface.DEFAULT_BOLD);
    urlText.setTextColor(getResources().getColor(R.color.lightBlue));
    urlText.setGravity(Gravity.LEFT | Gravity.CENTER);
    urlText.setText(url);

    urlText.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            String url = ((TextView) view).getText().toString();
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
      writer =
          new PrintWriter(
              new OutputStreamWriter(openFileOutput("playlist.content", Context.MODE_APPEND)),
              true);
      writer.println(url);
    } catch (IOException ioe) {
      ioe.printStackTrace();
    } finally {
      try {
        if (writer != null) writer.close();
      } catch (Exception ignored) {
      }
    }
  }

  private List<String> readUrlList() {
    File file = this.getFileStreamPath("playlist.content");
    if (file == null || !file.exists()) {
      return Collections.emptyList();
    }

    List<String> urlList = new ArrayList<>();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(openFileInput("playlist.content")));
      String url = null;
      while ((url = reader.readLine()) != null) {
        urlList.add(url);
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    } finally {
      try {
        if (reader != null) reader.close();
      } catch (Exception ignored) {
      }
    }
    return urlList;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  public void startPipPlayer(String url) {
    if (!Settings.canDrawOverlays(this)) {
      this.savedUrl = url;
      Intent request =
          new Intent(
              Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
              Uri.parse("package:" + this.getPackageName()));
      startActivityForResult(request, REQUEST_CODE_OVERLAY_PERMISSION);
    } else {
      Intent intent = new Intent(this, PlayerService.class);
      intent.putExtra(PlayerService.COMMAND, PlayerService.ACTION_START_PIP);
      intent.putExtra("data", url);
      startForegroundService(intent);
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
        if (Settings.canDrawOverlays(this)) {
            // 전달된 데이터 가져오기
            startPipPlayer(this.savedUrl);
        } else {
            Toast.makeText(this, "권한이 필요합니다!", Toast.LENGTH_SHORT).show();
        }
    }
  }
}
