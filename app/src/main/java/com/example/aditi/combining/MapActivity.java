package com.example.aditi.combining;

package info.androidhive.sqlite.view;

        import android.content.DialogInterface;
        import android.os.Bundle;
        import android.support.design.widget.CoordinatorLayout;
        import android.support.design.widget.FloatingActionButton;
        import android.support.v7.app.AlertDialog;
        import android.support.v7.app.AppCompatActivity;
        import android.support.v7.widget.DefaultItemAnimator;
        import android.support.v7.widget.LinearLayoutManager;
        import android.support.v7.widget.RecyclerView;
        import android.support.v7.widget.Toolbar;
        import android.text.TextUtils;
        import android.view.LayoutInflater;
        import android.view.View;
        import android.widget.EditText;
        import android.widget.TextView;
        import android.widget.Toast;

        import java.util.ArrayList;
        import java.util.List;

        import info.androidhive.sqlite.R;
        import info.androidhive.sqlite.database.DatabaseHelper;
        import info.androidhive.sqlite.database.model.Note;
        import info.androidhive.sqlite.utils.MyDividerItemDecoration;
        import info.androidhive.sqlite.utils.RecyclerTouchListener;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MapActivity extends AppCompatActivity {

    private NotesAdapter mAdapter;
    private List<Note> notesList = new ArrayList<>();
    private CoordinatorLayout coordinatorLayout;
    private RecyclerView recyclerView;
    private TextView noNotesView;

    private DatabaseHelper db;
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothLeScanner btScanner;
    //TextView peripheralTextView;
    TextToSpeech toSpeech;
    int result;
    String text;
    int rssi;
    String address;
    int flag=0;
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        coordinatorLayout = findViewById(R.id.coordinator_layout);
        recyclerView = findViewById(R.id.recycler_view);
        noNotesView = findViewById(R.id.empty_notes_view);

        db = new DatabaseHelper(this);

        notesList.addAll(db.getAllNotes());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
                                   @Override
                                   public void onClick(View view) {
                                       showNoteDialog(false, null, -1);
                                   }
                                   )};

        mAdapter = new NotesAdapter(this, notesList);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new MyDividerItemDecoration(this, LinearLayoutManager.VERTICAL, 16));
        recyclerView.setAdapter(mAdapter);

        toggleEmptyNotes();

        /**
         * On long press on RecyclerView item, open alert dialog
         * with options to choose
         * Edit and Delete
         * */
        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(this,
                recyclerView, new RecyclerTouchListener.ClickListener() {
            @Override
            public void onClick(View view, final int position) {
            }

            @Override
            public void onLongClick(View view, int position) {
                showActionsDialog(position);
            }
        }));



        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map2);

        toSpeech = new TextToSpeech(MapActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if (i == TextToSpeech.SUCCESS) {
                    result = toSpeech.setLanguage(Locale.UK);
                }
                else {
                    Toast.makeText(getApplicationContext(), "Feature not supported", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();


        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }

        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }

        startScanning();

    }

    /**
     * Inserting new note in db
     * and refreshing the list
     */
    private void createNote(String note) {
        // inserting note in db and getting
        // newly inserted note id
        long id = db.insertNote(note);

        // get the newly inserted note from db
        Note n = db.getNote(id);

        if (n != null) {
            // adding new note to array list at 0 position
            notesList.add(0, n);

            // refreshing the list
            mAdapter.notifyDataSetChanged();

            toggleEmptyNotes();
        }
    }

    /**
     * Updating note in db and updating
     * item in the list by its position
     */
    private void updateNote(String note, int position) {
        Note n = notesList.get(position);
        // updating note text
        n.setNote(note);

        // updating note in db
        db.updateNote(n);

        // refreshing the list
        notesList.set(position, n);
        mAdapter.notifyItemChanged(position);

        toggleEmptyNotes();
    }

    /**
     * Deleting note from SQLite and removing the
     * item from the list by its position
     */
    private void deleteNote(int position) {
        // deleting the note from db
        db.deleteNote(notesList.get(position));

        // removing the note from the list
        notesList.remove(position);
        mAdapter.notifyItemRemoved(position);

        toggleEmptyNotes();
    }

    /**
     * Opens dialog with Edit - Delete options
     * Edit - 0
     * Delete - 0
     */
    private void showActionsDialog(final int position) {
        CharSequence colors[] = new CharSequence[]{"Edit", "Delete"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose option");
        builder.setItems(colors, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showNoteDialog(true, notesList.get(position), position);
                } else {
                    deleteNote(position);
                }
            }
        });
        builder.show();
    }


    /**
     * Shows alert dialog with EditText options to enter / edit
     * a note.
     * when shouldUpdate=true, it automatically displays old note and changes the
     * button text to UPDATE
     */
    private void showNoteDialog(final boolean shouldUpdate, final Note note, final int position) {
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getApplicationContext());
        View view = layoutInflaterAndroid.inflate(R.layout.note_dialog, null);

        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilderUserInput.setView(view);

        final EditText inputNote = view.findViewById(R.id.note);
        TextView dialogTitle = view.findViewById(R.id.dialog_title);
        dialogTitle.setText(!shouldUpdate ? getString(R.string.lbl_new_note_title) : getString(R.string.lbl_edit_note_title));

        if (shouldUpdate && note != null) {
            inputNote.setText(note.getNote());
        }
        alertDialogBuilderUserInput
                .setCancelable(false)
                .setPositiveButton(shouldUpdate ? "update" : "save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogBox, int id) {

                    }
                })
                .setNegativeButton("cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialogBox, int id) {
                                dialogBox.cancel();
                            }
                        });

        final AlertDialog alertDialog = alertDialogBuilderUserInput.create();
        alertDialog.show();

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show toast message when no text is entered
                if (TextUtils.isEmpty(inputNote.getText().toString())) {
                    Toast.makeText(MainActivity.this, "Enter note!", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    alertDialog.dismiss();
                }

                // check if user updating note
                if (shouldUpdate && note != null) {
                    // update note by it's id
                    updateNote(inputNote.getText().toString(), position);
                } else {
                    // create new note
                    createNote(inputNote.getText().toString());
                }
            }
        });
    }

    /**
     * Toggling list and empty notes view
     */
    private void toggleEmptyNotes() {
        // you can check notesList.size() > 0

        if (db.getNotesCount() > 0) {
            noNotesView.setVisibility(View.GONE);
        } else {
            noNotesView.setVisibility(View.VISIBLE);
        }
    }


    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {


            address = result.getDevice().getAddress();
            rssi = result.getRssi();

            ShapefileFeatureTable mTable;
            try {
                mTable = new ShapefileFeatureTable(mShapefilePath);
                mFlayer = new FeatureLayer(mTable);
                mMapView.addLayer(mFlayer);
                Log.d("**ShapefileTest**", "SpatialReference : "+ mTable.getSpatialReference());
            } catch (FileNotFoundException e) {
                Log.d("**ShapefileTest**", "File not found in SDCard, nothing to load");
            }




            //peripheralTextView = (TextView) findViewById(R.id.PeripheralTextView);
            //peripheralTextView.setMovementMethod(new ScrollingMovementMethod());

            //peripheralTextView.append("Device Name: " + result.getDevice().getAddress() + " rssi: " + result.getRssi() + "\n");
            //peripheralTextView.setMovementMethod(new ScrollingMovementMethod());


            /*
            if(address.equals(result.getDevice().getAddress())) {
                ImageView imageView=(ImageView) findViewById(R.id.imageView1);
                imageView.setVisibility(View.VISIBLE);
            }
            */


            if(address.equals("88:4A:EA:7C:41:4C")){ //exit 1

                ImageView imageView=(ImageView) findViewById(R.id.imageView1);
                imageView.setVisibility(View.VISIBLE);
                imageView.setX(525);
                imageView.setY(1550);
                flag = 1;

            }

            if(address.equals("88:4A:EA:7C:43:25")){  //leftDoor1  --> right door 4 and 5

                ImageView imageView=(ImageView) findViewById(R.id.imageView1);
                imageView.setX(650);
                imageView.setY(1000);
                //imageView.setTop(10);
                //imageView.setRight(20);
                imageView.setVisibility(View.VISIBLE);
                flag = 2;
            }

            if(address.equals("88:C2:55:F7:08:2A")){ //Right door 1 --> right door 1 and 2

                ImageView imageView=(ImageView) findViewById(R.id.imageView1);
                imageView.setX(650);
                imageView.setY(1350);
                //imageView.setLeft(10);
                imageView.setVisibility(View.VISIBLE);
                flag = 3;
            }

            if(address.equals("88:4A:EA:7C:3E:F8")){ //Right door 2 ---> left door 5

                ImageView imageView=(ImageView) findViewById(R.id.imageView1);
                imageView.setX(400);
                imageView.setY(200);
                //imageView.setLeft(10);
                imageView.setVisibility(View.VISIBLE);
                flag = 4;
            }

            if(address.equals("88:4A:EA:7C:40:2B")){   //Right door 3  --> exit 2

                ImageView imageView=(ImageView) findViewById(R.id.imageView1);
                imageView.setX(525);
                imageView.setY(50);
                //imageView.setBottom(56);
                imageView.setVisibility(View.VISIBLE);
                flag = 5;
            }


            //if(/*Right door 4*/6){
            /*
                ImageView imageView=(ImageView) findViewById(R.id.imageView1);
                imageView.setX(100);
                imageView.setY(200);
                imageView.setTop(12);
                imageView.setVisibility(View.VISIBLE);
            }
            */
            //if(/*Right door 5*/7){
            /*
                ImageView imageView=(ImageView) findViewById(R.id.imageView1);
                imageView.setX(100);
                imageView.setY(200);
                imageView.setBottom(6);
                imageView.setVisibility(View.VISIBLE);
            }
            */


            //if(/*Left door 2*/8){
                /*
                ImageView imageView=(ImageView) findViewById(R.id.imageView1);
                imageView.setX(100);
                imageView.setY(200);
                imageView.setLeft(10);
                imageView.setRight(20);
                imageView.setVisibility(View.VISIBLE);
                */

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    public void Start(View v){

        if(flag==1) { //exit 1
            text = "You are at the exit";
            toSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.VISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor4);
            imageView2.setVisibility(View.INVISIBLE);
            ImageView imageView3 = (ImageView) findViewById(R.id.rightDoor2);
            imageView3.setVisibility(View.INVISIBLE);
            ImageView imageView4 = (ImageView) findViewById(R.id.leftDoor5);
            imageView4.setVisibility(View.INVISIBLE);
            ImageView imageView5 = (ImageView) findViewById(R.id.exit2);
            imageView5.setVisibility(View.INVISIBLE);
            //ImageView imageView1 = (ImageView) findViewById(R.id.imageView3);
            //imageView1.setVisibility(View.VISIBLE);

        }

        if(flag == 2) { //left door 1 --> right door 4 and 5

            text = "Move twenty two meters and proceed towards exit one";
            toSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.VISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor4);
            imageView2.setVisibility(View.VISIBLE);
            ImageView imageView3 = (ImageView) findViewById(R.id.rightDoor2);
            imageView3.setVisibility(View.INVISIBLE);
            ImageView imageView4 = (ImageView) findViewById(R.id.leftDoor5);
            imageView4.setVisibility(View.INVISIBLE);
            ImageView imageView5 = (ImageView) findViewById(R.id.exit2);
            imageView5.setVisibility(View.INVISIBLE);
        }

        if(flag == 3) { //right door 1 --> right door 1 and 2

            text = "Move eight meters and proceed towards exit one";
            toSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.VISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor2);
            imageView2.setVisibility(View.VISIBLE);
            ImageView imageView3 = (ImageView) findViewById(R.id.rightDoor4);
            imageView3.setVisibility(View.INVISIBLE);
            ImageView imageView4 = (ImageView) findViewById(R.id.leftDoor5);
            imageView4.setVisibility(View.INVISIBLE);
            ImageView imageView5 = (ImageView) findViewById(R.id.exit2);
            imageView5.setVisibility(View.INVISIBLE);

        }

        if(flag == 4) { //right door 2  ---> left door 5

            text = "Move fourteen meters and proceed towards exit two";
            toSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            ImageView imageView = (ImageView) findViewById(R.id.exit2);
            imageView.setVisibility(View.VISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.leftDoor5);
            imageView2.setVisibility(View.VISIBLE);
            ImageView imageView3 = (ImageView) findViewById(R.id.exit1);
            imageView3.setVisibility(View.INVISIBLE);
            ImageView imageView4 = (ImageView) findViewById(R.id.rightDoor4);
            imageView4.setVisibility(View.INVISIBLE);
            ImageView imageView5 = (ImageView) findViewById(R.id.rightDoor2);
            imageView5.setVisibility(View.INVISIBLE);;

        }

        if(flag == 5) { //right door 3  --> exit 2

            text = "You are at the exit";
            toSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            ImageView imageView = (ImageView) findViewById(R.id.exit2);
            imageView.setVisibility(View.VISIBLE);
            ImageView imageView5 = (ImageView) findViewById(R.id.exit1);
            imageView5.setVisibility(View.INVISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor4);
            imageView2.setVisibility(View.INVISIBLE);
            ImageView imageView3 = (ImageView) findViewById(R.id.rightDoor2);
            imageView3.setVisibility(View.INVISIBLE);
            ImageView imageView4 = (ImageView) findViewById(R.id.leftDoor5);
            imageView4.setVisibility(View.INVISIBLE);
            //ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor3);
            //imageView2.setVisibility(View.VISIBLE);

        }

/*
       // if(Right door 4-6) {

            text = "Take a left and move forward twenty two meters";
            toSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.VISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor4);
            imageView2.setVisibility(View.VISIBLE);

        }


        if(Right door 5-7) {

            text = "Take a left and move forward twenty four meters";
            toSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.VISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor5);
            imageView2.setVisibility(View.VISIBLE);

        }

*/


    }


    public void Stop(View v){

        if(flag==1) {       //exit
            toSpeech.stop();
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.INVISIBLE);
            //ImageView imageView1 = (ImageView) findViewById(R.id.imageView3);
            //imageView1.setVisibility(View.INVISIBLE);
        }

        if(flag == 2) {     //left door 1  --> right door 4 and 5
            toSpeech.stop();
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.INVISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor4);
            imageView2.setVisibility(View.INVISIBLE);
        }

        if(flag == 3) {         //right door 1 --> right door 1 and 2
            toSpeech.stop();
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.INVISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor2);
            imageView2.setVisibility(View.INVISIBLE);
        }

        if(flag == 4) {     //right door 2 ---> left door 5
            toSpeech.stop();
            ImageView imageView = (ImageView) findViewById(R.id.exit2);
            imageView.setVisibility(View.INVISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.leftDoor5);
            imageView2.setVisibility(View.INVISIBLE);
        }

        if(flag == 5) {     //right door 3 --> exit 2
            toSpeech.stop();
            ImageView imageView = (ImageView) findViewById(R.id.exit2);
            imageView.setVisibility(View.INVISIBLE);
            //ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor3);
            //imageView2.setVisibility(View.INVISIBLE);
        }
/*
        if(Right door 4-6) {
            toSpeech.stop();
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.INVISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor4);
            imageView2.setVisibility(View.INVISIBLE);
        }

        if(Right door 5-7) {
            toSpeech.stop();
            ImageView imageView = (ImageView) findViewById(R.id.exit1);
            imageView.setVisibility(View.INVISIBLE);
            ImageView imageView2 = (ImageView) findViewById(R.id.rightDoor5);
            imageView2.setVisibility(View.INVISIBLE);
        }
*/



    }

    public void startScanning() {

        //startScanningButton.setVisibility(View.INVISIBLE);
        //stopScanningButton.setVisibility(View.VISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.startScan(leScanCallback);
            }
        });
       // peripheralTextView.setText("");
    }

}
