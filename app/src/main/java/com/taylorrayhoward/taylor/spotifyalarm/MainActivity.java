package com.taylorrayhoward.taylor.spotifyalarm;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TimePicker;
import android.widget.Toast;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistSimple;

import static com.spotify.sdk.android.authentication.LoginActivity.REQUEST_CODE;
import static com.taylorrayhoward.taylor.spotifyalarm.Info.CLIENT_ID;
import static com.taylorrayhoward.taylor.spotifyalarm.Info.REDIRECT_URI;

public class MainActivity extends AppCompatActivity implements ConnectionStateCallback {
    //TODO Add database, add custom rows, add on click rows, finish whole project
    public String accessToken;
    public SpotifyApi api = new SpotifyApi();
    private AlarmDBHelper db = new AlarmDBHelper(this);
    ListAdapter listAdapter;
    ListView alarm_listview;
    SpotifyService spotify;
    List<PlaylistSimple> listOfPlaylists;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Calendar c = Calendar.getInstance();
                TimePickerDialog timePickerDialog = new TimePickerDialog(MainActivity.this,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker timePicker, int hour, int minute) {
                                setupPlaylistDialog(String.format("%02d", hour),String.format("%02d", minute));
                            }
                        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false);
                timePickerDialog.show();
            }

        });
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-read-private"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
        setupAlarmListView();
        spotify = api.getService();

    }

    private void setupPlaylistDialog(String hour, String minute){
        try {
            new getPlaylistSync().execute().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }
        final String h = hour;
        final String m = minute;
        ArrayList<String> names = new ArrayList<>();
        for (PlaylistSimple p : listOfPlaylists) {
            names.add(p.name);
        }
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        LayoutInflater inflater = getLayoutInflater();
        View convertView = inflater.inflate(R.layout.playlist_dialog, null);
        alertDialogBuilder.setView(convertView);
        ListView lv = (ListView) convertView.findViewById(R.id.playlist_listview);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.playlist_row, names);
        final AlertDialog dialog = alertDialogBuilder.show();
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                insert(h, m, listOfPlaylists.get(i).name, listOfPlaylists.get(i).id, listOfPlaylists.get(i).uri);

                dialog.dismiss();
            }
        });
        lv.setAdapter(adapter);
        //alertDialog.setCancelable(false); //TODO: Make them pick a playlist here

    }

    private void insert(String hour, String minute, String name, String id, String uri) {
        db.insertAlarm(String.valueOf(hour), String.valueOf(minute), "", name, id, uri);
        generateAlarmList();
    }

    private void setupAlarmListView(){
        alarm_listview = (ListView) findViewById(R.id.alarm_listview);
        alarm_listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Alarm a = (Alarm)adapterView.getItemAtPosition(i);
                Toast.makeText(getApplicationContext(), a.getTime(), Toast.LENGTH_SHORT).show();
            }
        });
        alarm_listview.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                Alarm a = (Alarm)adapterView.getItemAtPosition(i);
                db.deleteAlarm(a.getId());
                generateAlarmList();
                return true;
            }
        });
        generateAlarmList();
    }

    private void setupPlaylistListView(){

    }

    private void generateAlarmList() {
        ArrayList<Alarm> alarmList = db.getAllData();
        for (Alarm a : alarmList) {
            Log.d("AlarmTimes", a.getTime());
        }
        listAdapter = new AlarmAdapter(this, alarmList);
        alarm_listview.setAdapter(listAdapter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                accessToken = response.getAccessToken();
                api.setAccessToken(response.getAccessToken());
            }
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d("Login", "Successfully logged in");
        Toast.makeText(getApplicationContext(), "Log in successful", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLoggedOut() {

    }

    @Override
    public void onLoginFailed(int i) {
        Toast.makeText(getApplicationContext(), "Login failed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTemporaryError() {

    }

    @Override
    public void onConnectionMessage(String s) {

    }

    private class getPlaylistSync extends AsyncTask<Void, Void, Integer> {
        @Override
        protected Integer doInBackground(Void... voids) {
            Log.d("Async", "Getting playlist data");
            String id = spotify.getMe().id;
            listOfPlaylists  = spotify.getPlaylists(id).items;
            return 1;
        }
    }
}
