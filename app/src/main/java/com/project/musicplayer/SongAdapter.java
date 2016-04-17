package com.project.musicplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Sourabh on 12-Mar-16.
 */
public class SongAdapter extends BaseAdapter {

    //song list and layout
    private ArrayList<Song> songs;
    private LayoutInflater songInf;
    LinearLayout songLay;

    //constructor
    public SongAdapter(Context c, ArrayList<Song> theSongs) {
        songInf=LayoutInflater.from(c);
        songs=theSongs;
    }

    @Override
    public int getCount() {
        return songs.size();
    }

    @Override
    public Object getItem(int i) {
        return songs.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    //map to song layout
    @Override
    public View getView(int i, View view, final ViewGroup viewGroup) {
        songLay = (LinearLayout)songInf.inflate(R.layout.song, viewGroup, false);
        //get title and artist views
        TextView songView = (TextView)songLay.findViewById(R.id.song_title);
        TextView artistView = (TextView)songLay.findViewById(R.id.song_artist);
        //get song position
        final Song currSong = songs.get(i);
        //get title and artist strings
        songView.setText(currSong.getTitle());
        artistView.setText(currSong.getArtist());
        //set position as tag
        songLay.setTag(i);
        return songLay;
    }
}
