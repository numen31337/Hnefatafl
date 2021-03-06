package net.varunramesh.hnefatafl;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.alertdialogpro.AlertDialogPro;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.cocosw.bottomsheet.BottomSheet;
import com.gc.materialdesign.views.ButtonFloat;
import com.github.florent37.materialviewpager.MaterialViewPager;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatch;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatchConfig;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMultiplayer;
import com.google.example.games.basegameutils.BaseGameActivity;

import junit.framework.Assert;

import net.varunramesh.hnefatafl.game.PlayerActivity;
import net.varunramesh.hnefatafl.simulator.GameState;
import net.varunramesh.hnefatafl.simulator.GameType;
import net.varunramesh.hnefatafl.simulator.Player;
import net.varunramesh.hnefatafl.simulator.rulesets.Brandubh;
import net.varunramesh.hnefatafl.simulator.rulesets.FeltarHnefatafl;
import net.varunramesh.hnefatafl.simulator.rulesets.Ruleset;

import org.apache.commons.lang3.SerializationUtils;

import java.util.ArrayList;

import javax.xml.validation.Validator;

public class MainActivity extends BaseGameActivity {

    private MaterialViewPager mViewPager;
    private Toolbar toolbar;

    private final static int RC_SELECT_PLAYERS = 1;
    private final static int RC_SIGN_IN = 2;
    private final static int RC_VIEW_INBOX = 3;

    private final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Drawable tapestry = getResources().getDrawable(R.drawable.tapestry);
        final Palette tapestryPalette = Palette.generate(BitmapFactory.decodeResource(getResources(), R.drawable.tapestry));

        setTitle("");

        mViewPager = (MaterialViewPager) findViewById(R.id.materialViewPager);
        toolbar = mViewPager.getToolbar();

        if (toolbar != null) {
            setSupportActionBar(toolbar);

            final ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setDisplayShowHomeEnabled(false);
                actionBar.setHomeButtonEnabled(false);

                actionBar.setDisplayShowTitleEnabled(true);
                actionBar.setDisplayUseLogoEnabled(false);
            }
        }

        mViewPager.getViewPager().setAdapter(new FragmentStatePagerAdapter(getSupportFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                switch(position) {
                    case 0:
                        return new OngoingGamesFragment();
                    case 1:
                        return new RecyclerViewFragment();
                }
                throw new UnsupportedOperationException();
            }

            @Override
            public int getCount() {
                return 2;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                switch (position) {
                    case 0:
                        return "Ongoing Games";
                    case 1:
                        return "Match History";
                }
                return "";
            }
        });

        int defaultColor = getResources().getColor(R.color.buff);
        mViewPager.setColor(tapestryPalette.getVibrantColor(defaultColor), 0);
        mViewPager.setImageDrawable(tapestry, 0);

        mViewPager.getViewPager().setOffscreenPageLimit(mViewPager.getViewPager().getAdapter().getCount());
        mViewPager.getPagerTitleStrip().setViewPager(mViewPager.getViewPager());

        mViewPager.getViewPager().setCurrentItem(0);


        final BottomSheet.Builder bottomsheet = new BottomSheet.Builder(this)
            .title("Create a New Game...")
            .sheet(R.menu.menu_new_game)
            .listener((DialogInterface dialog, int item) -> {
                switch (item) {
                    case R.id.action_pass_and_play: {
                        showRulesetPicker((Ruleset ruleset) -> {
                            GameState gameState = new GameState(new GameType.PassAndPlay(), ruleset);
                            startActivity(PlayerActivity.createIntent(this, gameState));
                        });
                        break;
                    }
                    case R.id.action_online_match: {
                        if (!isSignedIn()) {
                            beginUserInitiatedSignIn();
                        } else {
                            Intent intent = Games.TurnBasedMultiplayer.getSelectOpponentsIntent(getApiClient(), 1, 1, true);
                            startActivityForResult(intent, RC_SELECT_PLAYERS);
                        }
                        break;
                    }
                    case R.id.action_player_vs_ai: {
                        showSidePickDialog((Player player) -> {
                            GameState gameState = new GameState(new GameType.PlayerVsAI(player), new Brandubh());
                            startActivity(PlayerActivity.createIntent(this, gameState));
                        });
                        break;
                    }
                }
            });

        final ButtonFloat newGame = (ButtonFloat)findViewById(R.id.newgame);
        newGame.setOnClickListener((View v) -> {
            bottomsheet.show();
        });
    }

    private void showSidePickDialog(Consumer<Player> callback) {
        final Mutable.Integer choice = new Mutable.Integer();

        AlertDialogPro.Builder builder = new AlertDialogPro.Builder(this);
        builder.setTitle("Pick A Side...")
                .setSingleChoiceItems(
                        new String[]{"Attacker", "Defender"}, -1, (DialogInterface sideDialog, int which) -> {
                            choice.value = which;
                        }
                )
                .setCancelable(true)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", (DialogInterface sideDialog, int which) -> {
                    Player player = (choice.value == 0) ? Player.ATTACKER : Player.DEFENDER;
                    callback.accept(player);
                }).show();
    }

    private void showRulesetPicker(Consumer<Ruleset> continuation) {
        final Mutable.Integer choice = new Mutable.Integer();

        AlertDialogPro.Builder builder = new AlertDialogPro.Builder(this);
        builder.setTitle("Pick A Variant...")
                .setSingleChoiceItems(
                        new String[]{"Feltar Hnefatafl (11x11)", "Brandubh (7x7)"}, -1, (DialogInterface sideDialog, int which) -> {
                            choice.value = which;
                        }
                )
                .setCancelable(true)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", (DialogInterface sideDialog, int which) -> {
                    switch (choice.value) {
                        case 0:
                            continuation.accept(new FeltarHnefatafl());
                            break;
                        case 1:
                            continuation.accept(new Brandubh());
                            break;
                        default:
                            throw new UnsupportedOperationException();
                    }
                }).show();
    }

    @Override
    public void onSignInFailed() {
        Log.e(TAG, "Sign in has failed.");
    }

    /**
     * Store whether or not the user has been redirected to a game from this activity. This is to
     * prevent the following situation.
     * 1. The player launches this activity from a notification.
     * 2. We redirect the game screen.
     * 3. The user hits back to come back to this activity.
     * 4. Since the intent data has not changed, we immediately send them back to the game screen.
     */
    private boolean redirectedToGame = false;

    @Override
    public void onSignInSucceeded() {
        Log.d(TAG, "Sign in has succeed.");

        // If this activity was launched from a notification then take them directly to the game.
        if (mHelper.getTurnBasedMatch() != null && !redirectedToGame) {
            redirectedToGame = true;
            startActivity(PlayerActivity.createIntent(this, mHelper.getTurnBasedMatch()));
            return;
        }
    }

    @Override
    public void onActivityResult(int request, int response, Intent intent) {
        super.onActivityResult(request, response, intent);

        switch (request) {
            case RC_SELECT_PLAYERS: {
                // user canceled
                if (response != Activity.RESULT_OK) return;

                // Get the invitee list.
                final ArrayList<String> invitees = intent.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);

                showRulesetPicker((Ruleset ruleset) -> {
                    // Get auto-match criteria.
                    Bundle autoMatchCriteria = null;
                    int minAutoMatchPlayers = intent.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
                    int maxAutoMatchPlayers = intent.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);
                    if (minAutoMatchPlayers > 0) {
                        autoMatchCriteria = RoomConfig.createAutoMatchCriteria(minAutoMatchPlayers, maxAutoMatchPlayers, 0);
                    } else {
                        autoMatchCriteria = null;
                    }

                    // TODO: Add Game Variant

                    TurnBasedMatchConfig tbmc = TurnBasedMatchConfig.builder()
                            .addInvitedPlayers(invitees)
                            .setAutoMatchCriteria(autoMatchCriteria)
                            .setVariant(rulesetToVariantId(ruleset))
                            .build();

                    // Create and start the match.
                    Games.TurnBasedMultiplayer
                            .createMatch(getApiClient(), tbmc)
                            .setResultCallback((TurnBasedMultiplayer.InitiateMatchResult initiateMatchResult) -> {
                                onInitiateMatchResult(initiateMatchResult);
                            });
                });
                break;
            }
            case RC_VIEW_INBOX: {
                // user canceled
                if (response != Activity.RESULT_OK) return;

                TurnBasedMatch match = intent.getParcelableExtra(Multiplayer.EXTRA_TURN_BASED_MATCH);
                Assert.assertNotNull("A match is returned from the inbox view.", match);
                startActivity(PlayerActivity.createIntent(this, match));

                break;
            }
        }
    }

    public void onInitiateMatchResult(TurnBasedMultiplayer.InitiateMatchResult result) {
        // Check if the status code is not success.
        Status status = result.getStatus();
        if (!status.isSuccess()) {
            Log.e(TAG, status.getStatusMessage());
            // TODO: showError(status.getStatusCode());
            return;
        }

        final TurnBasedMatch match = result.getMatch();
        if (match.getData() == null) {
            // We are the first player in the game.
            showSidePickDialog((Player player) -> {
                String myPlayerId = Games.Players.getCurrentPlayer(getApiClient()).getPlayerId();
                String myParticipantId = match.getParticipantId(myPlayerId);

                Optional<String> otherParticipantId = Stream.of(match.getParticipantIds()).filter(
                        (String id) -> !id.equals(myParticipantId)).findFirst();

                Assert.assertTrue("There is another player in the Match, other than me.", otherParticipantId.isPresent());

                String attackingParticipantId = (player == Player.ATTACKER) ? myParticipantId : otherParticipantId.get();
                String defendingParticipantId = (player == Player.ATTACKER) ? otherParticipantId.get() : myParticipantId;

                GameState gameState = new GameState(new GameType.OnlineMatch(attackingParticipantId, defendingParticipantId),
                        variantIdToRuleset(match.getVariant()));
                byte[] serialized = SerializationUtils.serialize(gameState);

                Games.TurnBasedMultiplayer.takeTurn(getApiClient(), match.getMatchId(), serialized, attackingParticipantId)
                        .setResultCallback((TurnBasedMultiplayer.UpdateMatchResult updateMatchResult) -> {
                            onUpdateMatchResult(updateMatchResult);
                        });
            });
        }
    }

    /** Open up the player's game inbox */
    public void openInbox() {
        Intent intent = Games.TurnBasedMultiplayer.getInboxIntent(getApiClient());
        startActivityForResult(intent, RC_VIEW_INBOX);
    }

    public void onUpdateMatchResult(TurnBasedMultiplayer.UpdateMatchResult updateMatchResult) {
        final TurnBasedMatch match = updateMatchResult.getMatch();
        if(match.getTurnStatus() == TurnBasedMatch.MATCH_TURN_STATUS_MY_TURN) {
            startActivity(PlayerActivity.createIntent(this, match));
        } else {
            Toast.makeText(this, "Invitation Sent", Toast.LENGTH_SHORT).show();
        }
    }

    public static int rulesetToVariantId(Ruleset ruleset) {
        if (ruleset instanceof Brandubh) {
            return 1;
        } else if (ruleset instanceof FeltarHnefatafl) {
            return 2;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public static Ruleset variantIdToRuleset(int variant) {
        switch (variant) {
            case 1:
                return new Brandubh();
            case 2:
                return new FeltarHnefatafl();
            default:
                throw new UnsupportedOperationException();
        }
    }
}
