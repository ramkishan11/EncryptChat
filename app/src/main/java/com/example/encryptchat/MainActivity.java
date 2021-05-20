package com.example.encryptchat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthMethodPickerLayout;
import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.example.encryptchat.R.*;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER = 2;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private Button encryptButton;
    private String mUsername;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    private ChildEventListener childEventListener;
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotoStorageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);



            mUsername = ANONYMOUS;
            //firebase refernec and instance to connect the database
            mFirebaseDatabase=FirebaseDatabase.getInstance();
            firebaseAuth=FirebaseAuth.getInstance();
            mMessagesDatabaseReference=mFirebaseDatabase.getReference().child("messages");
            mFirebaseStorage=FirebaseStorage.getInstance();
            mChatPhotoStorageReference=mFirebaseStorage.getReference().child("chat_photos");


            // Initialize references to views
            mProgressBar = (ProgressBar) findViewById(id.progressBar);
            mMessageListView = (ListView) findViewById(id.messageListView);
            mPhotoPickerButton = (ImageButton) findViewById(id.photoPickerButton);
            mMessageEditText = (EditText) findViewById(id.messageEditText);
            mSendButton = (Button) findViewById(id.sendButton);
            encryptButton=(Button)findViewById(id.encryptButton);

            // Initialize message ListView and its adapter
            List<FriendlyMessage> friendlyMessages = new ArrayList<>();
            mMessageAdapter = new MessageAdapter(this, layout.item_message, friendlyMessages);
            mMessageListView.setAdapter(mMessageAdapter);

            // Initialize progress bar
            mProgressBar.setVisibility(ProgressBar.INVISIBLE);




            // Send button sends a message and clears the EditText
            mSendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // TODO: Send messages on click
                    FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                    mMessagesDatabaseReference.push().setValue(friendlyMessage);
                    // Clear input box
                    mMessageEditText.setText("");
                }
            });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() == 0) {
                    mSendButton.setEnabled(false);
                } else {
                    mSendButton.setEnabled(true);
                }
            }
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        //Image picker button implemntation

        //this part fetches the image from the device
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this,"Button clicked",Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });



            encryptButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    showPopUp(view);

                }
            });


            attachDatabaseReadListener();
            mMessagesDatabaseReference.addChildEventListener(childEventListener);
            authStateListener=new FirebaseAuth.AuthStateListener() {
                @Override
                public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                    FirebaseUser user=firebaseAuth.getCurrentUser();
                    if(user!=null)
                    {
                        //user signed in
                        //Toast.makeText(MainActivity.this,"Succesfuly signed in",Toast.LENGTH_SHORT).show();
                        onSignedInitialize(user.getDisplayName());
                    }
                    else
                    {
                        //user signed out
                        onSignedOutCleanup();
                        AuthMethodPickerLayout customLayout = new AuthMethodPickerLayout
                                .Builder(layout.signinlayout)
                                .setGoogleButtonId(id.googlesignin)
                                .setEmailButtonId(id.mailsignin)
                                // ...

                                .build();
                        startActivityForResult(
                                AuthUI.getInstance()
                                        .createSignInIntentBuilder()
                                        .setAuthMethodPickerLayout(customLayout)
                                        .setIsSmartLockEnabled(false)
                                        .setAvailableProviders(Arrays.asList(
                                                new AuthUI.IdpConfig.GoogleBuilder().build(),
                                                new AuthUI.IdpConfig.EmailBuilder().build()
                                               ))
                                        .build(),
                                RC_SIGN_IN);
                    }
                }
            };

        }
    //implement sign out
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId())
        {
            case id.sign_out:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }

    }

    private void onSignedInitialize(String username) {
        mUsername=username;
       attachDatabaseReadListener();
    }
    private void onSignedOutCleanup()
    {
        mUsername=ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }


    public void showPopUp(View v)
        {
            PopupMenu popupMenu=new PopupMenu(this,v);
            MenuInflater menuInflater=popupMenu.getMenuInflater();
            menuInflater.inflate(menu.actions,popupMenu.getMenu());
            popupMenu.show();
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    Toast.makeText(MainActivity.this,"YOu clicked"+ menuItem.getTitle(),Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }


    //when we press the back button the app doesnot exit instead again launches mainactivity which results in a infinte loop
    //to avoid this if we cancel the sign in we should exit the app this is implemented using onactivityresult
    //we start the login flow using the startactivityforresult th info of whether the user is succesfully signed in or not is returned by the onactivityresult by using the result_ok and result_cancelled
    //if the result is cancelled we exit the app

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RC_SIGN_IN)
        {
            if(resultCode==RESULT_CANCELED)
            {
                Toast.makeText(MainActivity.this,"sign in cancelled!",Toast.LENGTH_SHORT).show();
                finish();
            }
            else
            {
                Toast.makeText(MainActivity.this,"sign in Successfull!",Toast.LENGTH_SHORT).show();

            }


        }
        if(requestCode==RC_PHOTO_PICKER && resultCode==RESULT_OK)
        {
            Uri selectedImageUri=data.getData();
            //get reference to storage
            StorageReference photoRef=mChatPhotoStorageReference.child(selectedImageUri.getLastPathSegment());
            // put thr file into the storage
            photoRef.putFile(selectedImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    photoRef.getDownloadUrl().addOnSuccessListener( new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            Uri downloadUrl=uri;
                            FriendlyMessage fr=new FriendlyMessage(null,mUsername,downloadUrl.toString());
                            mMessagesDatabaseReference.push().setValue(fr);

                        }
                    });
                }
            });

        }
    }
    protected void onResume() {

        super.onResume();
        firebaseAuth.addAuthStateListener(authStateListener);


    }




    protected void onPause(){
        super.onPause();
        firebaseAuth.removeAuthStateListener(authStateListener);
        detachDatabaseReadListener();
        childEventListener=null;
    }
    public void attachDatabaseReadListener()
    {
        if(childEventListener==null) {
            childEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    FriendlyMessage msg = snapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(msg);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };
        }
    }

    public void detachDatabaseReadListener() {
        if (childEventListener != null) {
            mMessagesDatabaseReference.removeEventListener(childEventListener);
            childEventListener=null;
        }


    }



}