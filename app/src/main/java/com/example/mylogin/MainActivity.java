package com.example.mylogin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

public class MainActivity extends AppCompatActivity {

    private static final int GAME_LENGTH_SECONDS = 10;

    private Button btnProfile, btnLogout, btnStartGame, btnTap, btnLeaderboard;
    private TextView tvGameScore, tvGameTimer, tvGameStatus, tvBestScore;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int currentScore = 0;
    private int secondsRemaining = GAME_LENGTH_SECONDS;
    private boolean gameRunning = false;
    private DatabaseReference currentLeaderboardRef;

    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            if (!gameRunning) {
                return;
            }

            secondsRemaining--;
            tvGameTimer.setText("Time: " + secondsRemaining);
            if (secondsRemaining <= 0) {
                finishGame();
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnProfile = findViewById(R.id.btnProfile);
        btnLogout = findViewById(R.id.btnLogout);
        btnStartGame = findViewById(R.id.btnStartGame);
        btnTap = findViewById(R.id.btnTap);
        btnLeaderboard = findViewById(R.id.btnLeaderboard);
        tvGameScore = findViewById(R.id.tvGameScore);
        tvGameTimer = findViewById(R.id.tvGameTimer);
        tvGameStatus = findViewById(R.id.tvGameStatus);
        tvBestScore = findViewById(R.id.tvBestScore);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentLeaderboardRef = FirebaseDatabase.getInstance()
                    .getReference("Leaderboard")
                    .child(user.getUid());
            ensureLeaderboardEntry(user, getIntent().getStringExtra("displayName"));
            loadBestScore();
        }

        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ProfileActivity.class)));

        btnLeaderboard.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, LeaderboardActivity.class)));

        btnStartGame.setOnClickListener(v -> startGame());
        btnTap.setOnClickListener(v -> {
            if (gameRunning) {
                currentScore++;
                tvGameScore.setText("Score: " + currentScore);
            }
        });

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void startGame() {
        handler.removeCallbacks(countdown);
        currentScore = 0;
        secondsRemaining = GAME_LENGTH_SECONDS;
        gameRunning = true;

        tvGameScore.setText("Score: 0");
        tvGameTimer.setText("Time: " + GAME_LENGTH_SECONDS);
        tvGameStatus.setText("Tap as fast as you can!");
        btnTap.setEnabled(true);
        btnStartGame.setEnabled(false);
        handler.postDelayed(countdown, 1000);
    }

    private void finishGame() {
        gameRunning = false;
        btnTap.setEnabled(false);
        btnStartGame.setEnabled(true);
        tvGameStatus.setText("Game over! Saving your best score...");
        saveHighScore();
    }

    private void ensureLeaderboardEntry(FirebaseUser user, String launchDisplayName) {
        if (launchDisplayName != null && !launchDisplayName.trim().isEmpty()) {
            currentLeaderboardRef.child("displayName").setValue(launchDisplayName);
            return;
        }

        currentLeaderboardRef.child("displayName").get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                return;
            }

            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.trim().isEmpty()) {
                currentLeaderboardRef.child("displayName").setValue(displayName);
            } else {
                FirebaseDatabase.getInstance()
                        .getReference("Users")
                        .child(user.getUid())
                        .child("displayName")
                        .get()
                        .addOnSuccessListener(nameSnapshot -> {
                            String savedName = nameSnapshot.getValue(String.class);
                            currentLeaderboardRef.child("displayName").setValue(
                                    savedName == null || savedName.trim().isEmpty() ? "Player" : savedName);
                        });
            }
        });
    }

    private void loadBestScore() {
        currentLeaderboardRef.child("highScore").get().addOnSuccessListener(snapshot -> {
            Object value = snapshot.getValue();
            long bestScore = value instanceof Number ? ((Number) value).longValue() : 0;
            tvBestScore.setText("Your best: " + bestScore);
        });
    }

    private void saveHighScore() {
        if (currentLeaderboardRef == null) {
            tvGameStatus.setText("Sign in to save your score.");
            return;
        }

        final int scoreToSave = currentScore;
        currentLeaderboardRef.child("highScore").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Object value = currentData.getValue();
                long oldBest = value instanceof Number ? ((Number) value).longValue() : 0;
                if (scoreToSave > oldBest) {
                    currentData.setValue(scoreToSave);
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    tvGameStatus.setText("Could not save score. Check your connection.");
                    return;
                }

                Object value = currentData.getValue();
                long bestScore = value instanceof Number ? ((Number) value).longValue() : scoreToSave;
                tvBestScore.setText("Your best: " + bestScore);
                tvGameStatus.setText("Game over! Your best score: " + bestScore);
            }
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(countdown);
        super.onDestroy();
    }
}
