package com.project.musicplayer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by Sourabh on 13-Mar-16.
 */
public class MusicService extends Service implements SensorEventListener, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, RecognitionListener{

    //media player
    private MediaPlayer player;
    //song list
    private ArrayList<Song> songs;
    //current position
    private int songPosn;
    //title of current song
    private String songTitle="";
    //notification id
    private static final int NOTIFY_ID = 1;
    //shuffle flag and random
    private boolean shuffle = false;
    private Random rand;
    //binder
    private final IBinder musicBind = new MusicBinder();
    private AudioManager audioManager;
    //proximity sensor
    private SensorManager mSensorManager;
    private Sensor mProximity;

    //Speech Recognizer
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private String LOG_TAG = "MusicService";

    boolean isPaused;

    //activity will bind to service
    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    //release resources when unbind service from activity
    @Override
    public boolean onUnbind(Intent intent) {
        if(mSensorManager != null)
            mSensorManager.unregisterListener(this);
        player.stop();
        player.release();
        return false;
    }

    //on service destroy
    @Override
    public void onDestroy() {
        if (speech != null) {
            speech.stopListening();
            speech.destroy();
            Log.i(LOG_TAG, "destroy");
        }
        if(mSensorManager != null)
            mSensorManager.unregisterListener(this);
        stopForeground(true);
        if(player != null) {
            player.release();
        }
        super.onDestroy();
    }

    //on media player track finished
    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        //check if playback has reached the end of a track
        if(player.getCurrentPosition()>0) {
            mediaPlayer.reset();
            playNext();
        }
    }

    //on media player error
    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        Log.e("MUSIC PLAYER", "Playback Error");
        mediaPlayer.reset();
        return false;
    }

    //when media player ready to play
    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        //start playback
        mediaPlayer.start();
        //notification
        Intent notIntent = new Intent(this, MainActivity.class);
        notIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendInt = PendingIntent.getActivity(this, 0, notIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(this);

        builder.setContentIntent(pendInt)
                .setSmallIcon(R.drawable.play)
                .setTicker(songTitle)
                .setOngoing(true)
                .setContentTitle("Playing")
                .setContentText(songTitle);
        Notification not = builder.build();

        startForeground(NOTIFY_ID, not);

        // Broadcast intent to activity to let it know the media player has been prepared
        Intent onPreparedIntent = new Intent("MEDIA_PLAYER_PREPARED");
        LocalBroadcastManager.getInstance(this).sendBroadcast(onPreparedIntent);
    }

    //on service created
    @Override
    public void onCreate() {
        //create the service
        super.onCreate();
        //initialize position
        songPosn=0;
        //create player
        player = new MediaPlayer();
        initMusicPlayer();
        rand = new Random();

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        //audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);

        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        //speech.startListening(recognizerIntent);
    }

    //set shuffle on or off
    public void setShuffle() {
        if(shuffle) {
            shuffle = false;
        }
        else
            shuffle = true;
    }

    //initialize music player
    public void initMusicPlayer() {
        //set player properties
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        //set listeners
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
    }

    //pass song list
    public void setList(ArrayList<Song> theSongs) {
        songs = theSongs;
    }

    /*
    //on another activity priority (example incoming call)
    @Override
    public void onAudioFocusChange(int i) {
        switch(i) {

            case AudioManager.AUDIOFOCUS_GAIN:
                //resume playback
                if(player == null) {
                    initMusicPlayer();
                }
                else if(!player.isPlaying()) {
                    player.start();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                //lost focus: stop playback and release media player
                if(player.isPlaying()) {
                    player.stop();
                }
                player.release();
                player=null;
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (player.isPlaying()) {
                    player.pause();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (player.isPlaying()) {
                    player.setVolume(0.1f, 0.1f);
                }
                break;
        }
    }
    */

    //on proximity sensor changed
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.values[0] == 0) {
            if(player.isPlaying()) {
                player.pause();
                isPaused = true;
            }
            speech.stopListening();
            speech.startListening(recognizerIntent);
            //Delay playback for 4 secs
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Do something after 4s = 4000ms
                    if(isPaused)
                        player.start();
                }
            }, 4000);
                speech.stopListening();
        }
    }

    //on proximity accuracy changed
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // Do something here if sensor accuracy changes
    }

    //binder
    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    //play a song
    public void playSong() {
        //play
        player.reset();
        //get song
        Song playSong = songs.get(songPosn);
        //get title
        songTitle = playSong.getTitle();
        //get id
        long currSong = playSong.getID();
        //set uri
        Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currSong);
        //set the data source
        try {
            player.setDataSource(getApplicationContext(), trackUri);
        }
        catch (Exception e) {
            Log.e("MUSIC SERVICE", "Error setting data source", e);
        }
        player.prepareAsync();
    }

    //playback methods
    public void setSong(int songIndex) {
        songPosn = songIndex;
    }

    public int getPosn() {
        return player.getCurrentPosition();
    }

    public int getDur() {
        return player.getDuration();
    }

    public boolean isPng() {
        return player.isPlaying();
    }

    public void pausePlayer() {
        player.pause();
    }

    public void seek(int posn) {
        player.seekTo(posn);
    }

    public void go() {
        player.start();
    }


    //skip to previous track
    public void playPrev() {
        songPosn--;
        if(songPosn<0) {
            songPosn = songs.size()-1;
        }
        playSong();
    }
    //skip to next track
    public void playNext() {
        if(shuffle) {
            int newSong = songPosn;
            while(newSong == songPosn) {
                newSong = rand.nextInt(songs.size());
            }
            songPosn = newSong;
        }
        else {
            songPosn++;
            if(songPosn>=songs.size()) {
                songPosn = 0;
            }
        }
        playSong();
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(LOG_TAG, "onBeginningOfSpeech");
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i(LOG_TAG, "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(LOG_TAG, "onEndOfSpeech");
    }

    @Override
    public void onError(int errorCode) {
        String errorMessage = getErrorText(errorCode);
        Log.d(LOG_TAG, "FAILED " + errorMessage);
        //speech.stopListening();
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i(LOG_TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i(LOG_TAG, "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i(LOG_TAG, "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results) {
        Log.i(LOG_TAG, "onResults");
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(matches != null) {
            if (matches.contains("start")|| matches.contains("play")) {
                playSong();
                isPaused = false;
                Log.d("Voice", "start");
            } else if (matches.contains("resume")) {
                player.start();
                isPaused = false;
                Log.d("Voice","resume");
            }
            else if (matches.contains("stop") || matches.contains("pause")) {
                pausePlayer();
                isPaused = false;
                Log.d("Voice", "stop");
            } else if (matches.contains("next")) {
                playNext();
                isPaused = false;
                Log.d("Voice", "next");
            } else if (matches.contains("previous")) {
                playPrev();
                isPaused = false;
                Log.d("Voice", "prev");
            }
            else {
                Toast.makeText(getApplicationContext(),"Please try again!",Toast.LENGTH_SHORT).show();
            }
        }
        speech.stopListening();
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
    }

    public String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }
}