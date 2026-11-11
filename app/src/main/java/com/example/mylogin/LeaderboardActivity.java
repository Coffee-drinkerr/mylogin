package com.example.mylogin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    private static final long LOAD_TIMEOUT_MS = 12_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout leaderboardContainer;
    private ProgressBar progressBar;
    private Button btnRetry, btnBackToGame;
    private Runnable timeoutRunnable;
    private int requestNumber = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        leaderboardContainer = findViewById(R.id.leaderboardContainer);
        progressBar = findViewById(R.id.leaderboardProgressBar);
        btnRetry = findViewById(R.id.btnRetryLeaderboard);
        btnRetry.setOnClickListener(v -> loadLeaderboard());
        btnBackToGame = findViewById(R.id.btnBackToGame);
        btnBackToGame.setOnClickListener(v -> finish());
        loadLeaderboard();
    }

    private void loadLeaderboard() {
        int thisRequest = ++requestNumber;
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }

        leaderboardContainer.removeAllViews();
        progressBar.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.GONE);

        timeoutRunnable = () -> {
            if (thisRequest != requestNumber) {
                return;
            }
            progressBar.setVisibility(View.GONE);
            showMessage("Couldn't load scores. Check the Realtime Database URL, internet connection, and read access rules.");
            btnRetry.setVisibility(View.VISIBLE);
        };
        handler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS);

        try {
            Query usersQuery = FirebaseDatabase.getInstance()
                    .getReference("Leaderboard")
                    .orderByChild("highScore");

            usersQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (thisRequest != requestNumber) {
                        return;
                    }
                    finishLoading();

                    List<PlayerScore> players = new ArrayList<>();
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        String displayName = userSnapshot.child("displayName").getValue(String.class);
                        if (displayName == null || displayName.trim().isEmpty()) {
                            displayName = "Player";
                        }

                        Object scoreValue = userSnapshot.child("highScore").getValue();
                        long score = scoreValue instanceof Number ? ((Number) scoreValue).longValue() : 0;
                        players.add(new PlayerScore(displayName, score));
                    }

                    players.sort(Comparator
                            .comparingLong((PlayerScore player) -> player.score)
                            .reversed()
                            .thenComparing(player -> player.displayName.toLowerCase()));

                    showPlayers(players);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (thisRequest != requestNumber) {
                        return;
                    }
                    finishLoading();
                    showMessage("Could not load scores: " + error.getMessage());
                    btnRetry.setVisibility(View.VISIBLE);
                }
            });
        } catch (DatabaseException error) {
            if (thisRequest == requestNumber) {
                finishLoading();
                showMessage("Firebase Realtime Database is not configured correctly: " + error.getMessage());
                btnRetry.setVisibility(View.VISIBLE);
            }
        }
    }

    private void finishLoading() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }
        progressBar.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);
    }

    private void showPlayers(List<PlayerScore> players) {
        leaderboardContainer.removeAllViews();
        if (players.isEmpty()) {
            showMessage("No players yet. Be the first to play!");
            return;
        }

        for (int i = 0; i < players.size(); i++) {
            PlayerScore player = players.get(i);
            TextView row = new TextView(this);
            row.setText((i + 1) + ". " + player.displayName + " — " + player.score);
            row.setTextSize(18);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(16, 14, 16, 14);
            leaderboardContainer.addView(row);
        }
    }

    private void showMessage(String message) {
        leaderboardContainer.removeAllViews();
        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setTextSize(16);
        textView.setPadding(16, 20, 16, 20);
        leaderboardContainer.addView(textView);
    }

    @Override
    protected void onDestroy() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }
        super.onDestroy();
    }

    private static class PlayerScore {
        final String displayName;
        final long score;

        PlayerScore(String displayName, long score) {
            this.displayName = displayName;
            this.score = score;
        }
    }
}
