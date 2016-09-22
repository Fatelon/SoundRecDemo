package com.fatelon.soundrecdemo;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    final static public String DROPBOX_APP_KEY = "ye9ndxz1uc0vhzt";

    final static public String DROPBOX_APP_SECRET = "yzr4p2nn7xdtbhy";

    final static public Session.AccessType ACCESS_TYPE = Session.AccessType.DROPBOX;

    private DropboxAPI<AndroidAuthSession> mDBApi;

    private static final String TAG = "MediaRecording";

    private Handler customHandler = new Handler();

    private Button mButtRec;

    private Button mButtStop;

    private Button mButtSend;

    private TextView mTimerText;

    private MediaRecorder recorder;

    private File audiofile = null;

    private long startTime = 0L;

    private long timeInMilliseconds = 0L;

    private long timeSwapBuff = 0L;

    private long updatedTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mButtRec = (Button) findViewById(R.id.button_rec);
        mButtStop = (Button) findViewById(R.id.button_stop);
        mButtSend = (Button) findViewById(R.id.button_send);
        mTimerText = (TextView) findViewById(R.id.timer_textview);

        mButtRec.setEnabled(true);
        mButtStop.setEnabled(false);
        mButtSend.setEnabled(false);

        mButtRec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRec();
            }
        });

        mButtStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRec();
            }
        });

        mButtSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendRec();
            }
        });
        conntoDB();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mDBApi.getSession().authenticationSuccessful()) {
            try {
                // Required to complete auth, sets the access token on the session
                mDBApi.getSession().finishAuthentication();

                String accessToken = mDBApi.getSession().getOAuth2AccessToken();
            } catch (IllegalStateException e) {
                Log.i("DbAuthLog", "Error authenticating", e);
            }
        }
    }

    private void startRec() {
        mButtRec.setEnabled(false);
        mButtStop.setEnabled(true);
        mButtSend.setEnabled(false);
        File dir = Environment.getExternalStorageDirectory();
        try {
            audiofile = File.createTempFile("sound", ".3gp", dir);
        } catch (IOException e) {
            Log.e(TAG, "external storage access error");
            return;
        }
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile(audiofile.getAbsolutePath());
        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare error");
            e.printStackTrace();
            return;
        }
        recorder.start();
        startTimer();
    }

    private void stopRec() {
        mButtRec.setEnabled(true);
        mButtStop.setEnabled(false);
        mButtSend.setEnabled(true);
        recorder.stop();
        recorder.release();
        stopTimer();
    }

    private void sendRec() {
        mButtRec.setEnabled(true);
        mButtStop.setEnabled(false);
        mButtSend.setEnabled(false);

        SendRec sr = new SendRec(this, audiofile, mDBApi);
        sr.execute();

    }

    private void startTimer() {
        startTime = SystemClock.uptimeMillis();
        customHandler.postDelayed(updateTimerThread, 0);
    }

    private void stopTimer() {
        timeSwapBuff =  0L;
        customHandler.removeCallbacks(updateTimerThread);
        mTimerText.setText(getResources().getText(R.string.timer_start_value));
    }

    private Runnable updateTimerThread = new Runnable() {
        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;
            int secs = (int) (updatedTime / 1000);
            int mins = secs / 60;
            secs = secs % 60;
            int milliseconds = (int) (updatedTime % 1000);
            mTimerText.setText("" + mins + ":"
                            + String.format("%02d", secs) + ":"
                            + String.format("%03d", milliseconds));
            customHandler.postDelayed(this, 0);
        }
    };

    private void conntoDB() {
        AppKeyPair appKeys = new AppKeyPair(DROPBOX_APP_KEY, DROPBOX_APP_SECRET);
        AndroidAuthSession session = new AndroidAuthSession(appKeys);
        mDBApi = new DropboxAPI<AndroidAuthSession>(session);
        mDBApi.getSession().startOAuth2Authentication(this);
    }

    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(DROPBOX_APP_KEY, DROPBOX_APP_SECRET);
        AndroidAuthSession session;
        String[] stored = null; //getKeys();
        if (stored != null) {
            AccessTokenPair accessToken = new AccessTokenPair(stored[0],stored[1]);
            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE, accessToken);
        } else {
            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE);
        }
        return session;
    }


    public class SendRec extends AsyncTask<Void,Void,Void> {

        private Context context;

        private File file = null;

        DropboxAPI<AndroidAuthSession> mDB;

        public SendRec(Context context, File file, DropboxAPI<AndroidAuthSession> mDBApi){
            this.context = context;
            this.file = file;
            this.mDB = mDBApi;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
//                File dir = Environment.getExternalStorageDirectory();
//                try {
//                    audiofile = File.createTempFile("sound", ".3gp", dir);
//                } catch (IOException e) {
//                    Log.e(TAG, "external storage access error");
//                    return;
//                }
//                File file = new File(audiofile.getPath());
                FileInputStream inputStream = new FileInputStream(file);
                mDB.putFile("SoundRecDemo/" + file.getName(), inputStream, file.length(), null, null);
                String s = "kd";
//            DropboxAPI.Entry response = mDBApi.putFile("SoundRecDemo/" + audiofile.getName(), inputStream, audiofile.length(), null, null);
//            Log.i(TAG, "The uploaded file's rev is:" + response.rev);
//            DropboxAPI.Entry response = mDBApi.putFile("/SoundRecDemo/" + audiofile.getName(), inputStream, audiofile.length(), null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }

    }
}
