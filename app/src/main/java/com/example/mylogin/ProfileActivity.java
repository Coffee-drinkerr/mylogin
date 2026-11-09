package com.example.mylogin;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private ImageView ivProfilePicture;
    private EditText etProfileName, etProfilePhone;
    private Button btnSaveProfile;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference userDbRef;
    private StorageReference storageRef;

    private Uri imageUri = null;
    private Uri cameraImageUri;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    imageUri = uri;
                    Glide.with(this).load(imageUri).into(ivProfilePicture);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) {
                    imageUri = cameraImageUri;
                    Glide.with(this).load(imageUri).into(ivProfilePicture);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        userDbRef = FirebaseDatabase.getInstance().getReference("Users").child(currentUser.getUid());
        storageRef = FirebaseStorage.getInstance().getReference("profile_pictures");

        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        etProfileName = findViewById(R.id.etProfileName);
        etProfilePhone = findViewById(R.id.etProfilePhone);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        progressBar = findViewById(R.id.profileProgressBar);

        loadUserProfile();

        ivProfilePicture.setOnClickListener(v -> selectImage());
        btnSaveProfile.setOnClickListener(v -> saveUserProfile());
    }

    private void selectImage() {
        new AlertDialog.Builder(this)
                .setTitle("Change profile picture")
                .setItems(new String[]{"Take a picture", "Choose from library"}, (dialog, which) -> {
                    if (which == 0) {
                        takePicture();
                    } else {
                        imagePickerLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private void takePicture() {
        try {
            File photoFile = File.createTempFile("profile_picture_", ".jpg", getCacheDir());
            cameraImageUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile
            );
            cameraLauncher.launch(cameraImageUri);
        } catch (IOException e) {
            Toast.makeText(this, "Could not open the camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadUserProfile() {
        if (currentUser.getDisplayName() != null) {
            etProfileName.setText(currentUser.getDisplayName());
        }

        if (currentUser.getPhotoUrl() != null && !isFinishing() && !isDestroyed()) {
            Glide.with(this).load(currentUser.getPhotoUrl()).into(ivProfilePicture);
        }

        // Changed to addListenerForSingleValueEvent to fetch initial data safely once
        userDbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;

                if (snapshot.exists()) {
                    if (snapshot.hasChild("phone")) {
                        String phone = snapshot.child("phone").getValue(String.class);
                        etProfilePhone.setText(phone);
                    }
                    if (snapshot.hasChild("profileImageUrl")) {
                        String imageUrl = snapshot.child("profileImageUrl").getValue(String.class);
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            Glide.with(ProfileActivity.this).load(imageUrl).into(ivProfilePicture);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Failed to load data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserProfile() {
        String name = etProfileName.getText().toString().trim();
        String phone = etProfilePhone.getText().toString().trim();

        if (name.isEmpty()) {
            etProfileName.setError("Name cannot be empty");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        if (imageUri != null) {
            StorageReference fileRef = storageRef.child(currentUser.getUid() + ".jpg");
            fileRef.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot ->
                            fileRef.getDownloadUrl()
                                    .addOnSuccessListener(downloadUri ->
                                            updateUserDatabase(name, phone, downloadUri.toString()))
                                    .addOnFailureListener(e ->
                                            showStorageError("Photo uploaded, but its download URL could not be loaded", e)))
                    .addOnFailureListener(e -> showStorageError("Photo upload failed", e));
        } else {
            updateUserDatabase(name, phone, null);
        }
    }

    private void showStorageError(String action, Exception error) {
        progressBar.setVisibility(View.GONE);
        String detail = error.getMessage() != null ? error.getMessage() : "Unknown Firebase Storage error";
        Toast.makeText(this,
                action + ": " + detail + ". Check that the Firebase Storage bucket exists and its rules allow this signed-in user.",
                Toast.LENGTH_LONG).show();
    }

    private void updateUserDatabase(String name, String phone, String imageUrl) {
        UserProfileChangeRequest.Builder profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name);

        if (imageUrl != null) {
            profileUpdates.setPhotoUri(Uri.parse(imageUrl));
        }

        currentUser.updateProfile(profileUpdates.build()).addOnCompleteListener(task -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("displayName", name);
            updates.put("phone", phone);
            if (imageUrl != null) {
                updates.put("profileImageUrl", imageUrl);
            }

            userDbRef.updateChildren(updates).addOnCompleteListener(dbTask -> {
                progressBar.setVisibility(View.GONE);
                if (dbTask.isSuccessful()) {
                    Toast.makeText(ProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ProfileActivity.this, "Failed to update profile: " +
                            (dbTask.getException() != null ? dbTask.getException().getMessage() : ""), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}