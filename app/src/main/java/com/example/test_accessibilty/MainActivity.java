package com.example.test_accessibilty;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;


public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    TextToSpeech mTTS = null;
    private final int ACT_CHECK_TTS_DATA = 1000;
    private final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 2000;
    private int permissionCount = 0;
    private String mAudioFilename = "";
    private final String mUtteranceID = "tts";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final EditText edit_text = (EditText) findViewById(R.id.edit_text);
        final Button btn_speak = (Button) findViewById(R.id.btn_speak);
        btn_speak.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                speak(edit_text.getText().toString().trim(), 1);
            }
        });

        final Button btn_save = (Button) findViewById(R.id.btn_save);
        btn_save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveToAudioFile(edit_text.getText().toString().trim());
            }
        });

        //動態請求權限
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE);


        // 新增 audio file location
        File sddir = new File(Environment.getExternalStorageDirectory() + "/TTSEngine/");
        sddir.mkdirs();
        mAudioFilename = sddir.getAbsolutePath() + "/" + mUtteranceID + ".wav";

        /**
         * 1：檢查是否有 TTS voice data
         * */
        Intent ttsIntent = new Intent();
        ttsIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(ttsIntent, ACT_CHECK_TTS_DATA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    permissionCount++;
            default:
                break;
        }
    }

    //將TTS Engine輸出的Audio Stream作為永久的音訊檔儲存在當前的儲存空間，對需重複播放的語音內容實現快速的回放功能
    //通過TTS的synthesizeToFile方法，將合成的語音Stream儲存在參數所指定位址
    private void saveToAudioFile(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTTS.synthesizeToFile(text, null, new File(mAudioFilename), mUtteranceID);
        } else {
            HashMap<String, String> hm = new HashMap();
            hm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, mUtteranceID);
            mTTS.synthesizeToFile(text, hm, mAudioFilename);
        }


        //語音功能的Completion Callback:可以利用OnUtteranceCompletedListener追加Speak()執行之後的額外操作
        //把HashMap參數傳進Listener中，作為條件判斷依據
        mTTS.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
            public void onUtteranceCompleted(String uid) {
                if (uid.equals(mUtteranceID)) {
                    Toast.makeText(MainActivity.this, "Saved to " + mAudioFilename, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    //執行Speak的具體方法
    private void speak(String text, int mode) {
        if (mode == 1) {
            mTTS.speak(text, TextToSpeech.QUEUE_ADD, null);//將語音訊息新增到當前任務列隊（Queue）之後
        } else {
            mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);//會中斷當前正在執行的任務（清除當前語音任務，轉而執行新的Queue任務）
        }
    }

    /**
     * 2：如果需要，安裝TTS資源
     * “ACTION_INSTALL_TTS_DATA” intent將使用者引入Android market中的TTS下載介面，下載完成後將自動完成安裝
     */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACT_CHECK_TTS_DATA) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // data exists, so we instantiate the TTS engine
                mTTS = new TextToSpeech(this, this);
            } else {
                // data is missing, so we start the TTS installation process
                Intent installIntent = new Intent();
                installIntent.setAction(
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    /**
     * 3：實現TextToSpeech.OnInitListener
       OnInitListener（）是通知系統當前TTS Engine已經載入完成，並處於可用狀態
     */
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            if (mTTS != null) {
//                根據需求設定語言：
//                int result = mTTS.setLanguage(Locale.US);
//                int result = mTTS.setLanguage(Locale.TRADITIONAL_CHINESE);
//                相較強制使用預定語言，建議用Locale.getDefault() 方法根據使用者預設的地區設定來選擇合適的語言庫
                int result = mTTS.setLanguage(Locale.getDefault());

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS language is not supported", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "TTS is ready", Toast.LENGTH_LONG).show();
                    speak("TTS is ready", 0);
                }
            }
        } else {
            Toast.makeText(this, "TTS initialization failed", Toast.LENGTH_LONG).show();
        }
    }

    //釋放當前TTS實體所佔用的資源
    @Override
    protected void onDestroy() {
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
        super.onDestroy();
    }
}
