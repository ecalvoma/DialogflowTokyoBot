package com.bae.dialogflowbot;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bae.dialogflowbot.adapters.ChatAdapter;
import com.bae.dialogflowbot.helpers.SendMessageInBg;
import com.bae.dialogflowbot.interfaces.BotReply;
import com.bae.dialogflowbot.models.Message;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements BotReply {

  public static final Integer RecordAudioRequestCode = 1;
  private SpeechRecognizer speechRecognizer;
  RecyclerView chatView;
  ChatAdapter chatAdapter;
  List<Message> messageList = new ArrayList<>();
  EditText editMessage;
  ImageButton btnSend;
  ImageButton btnMic;
  TextToSpeech tts;

  //dialogFlow
  private SessionsClient sessionsClient;
  private SessionName sessionName;
  private String uuid = UUID.randomUUID().toString();
  private String TAG = "mainactivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {checkPermission(); }
    chatView = findViewById(R.id.chatView);
    editMessage = findViewById(R.id.editMessage);
    btnMic = findViewById(R.id.btnMic);
    btnSend = findViewById(R.id.btnSend);
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

    chatAdapter = new ChatAdapter(messageList, this);
    chatView.setAdapter(chatAdapter);

    btnSend.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        String message = editMessage.getText().toString();
        if (!message.isEmpty()) {
          messageList.add(new Message(message, false));
          editMessage.setText("");
          sendMessageToBot(message);
          Objects.requireNonNull(chatView.getAdapter()).notifyDataSetChanged();
          Objects.requireNonNull(chatView.getLayoutManager())
                  .scrollToPosition(messageList.size() - 1);
        } else {
          Toast.makeText(MainActivity.this, "Please enter text!", Toast.LENGTH_SHORT).show();
        }
      }
    });

    final Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

    speechRecognizer.setRecognitionListener(new RecognitionListener() {
      @Override
      public void onReadyForSpeech(Bundle params) {

      }

      @Override
      public void onBeginningOfSpeech() {
        Log.i("A", "esta escuchando");
        editMessage.setText("");
        editMessage.setHint("Listening...");
      }

      @Override
      public void onRmsChanged(float rmsdB) {

      }

      @Override
      public void onBufferReceived(byte[] buffer) {

      }

      @Override
      public void onEndOfSpeech() {
        editMessage.setText("");
        editMessage.setHint("Enter your message");
      }

      @Override
      public void onError(int error) {

      }

      @Override
      public void onResults(Bundle results) {
        ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        editMessage.setText(data.get(0));
      }

      @Override
      public void onPartialResults(Bundle partialResults) {

      }

      @Override
      public void onEvent(int eventType, Bundle params) {

      }
    });

    btnMic.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP){
          speechRecognizer.stopListening();
          btnMic.setImageResource(R.drawable.ic_mic);
        }
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN){
//          Log.i("A", "ha escuchado algo");
          btnMic.setImageResource(R.drawable.ic_mic);
          speechRecognizer.startListening(speechRecognizerIntent);
        }
        return false;
      }
    });

    setUpBot();
  }

  private void checkPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RecordAudioRequestCode);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == RecordAudioRequestCode && grantResults.length > 0) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
      }
    }
  }

  private void setUpBot() {
    try {
      InputStream stream = this.getResources().openRawResource(R.raw.credential);
      GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
              .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
      String projectId = ((ServiceAccountCredentials) credentials).getProjectId();

      SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
      SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
              FixedCredentialsProvider.create(credentials)).build();
      sessionsClient = SessionsClient.create(sessionsSettings);
      sessionName = SessionName.of(projectId, uuid);

      Log.d(TAG, "projectId : " + projectId);
    } catch (Exception e) {
      Log.d(TAG, "setUpBot: " + e.getMessage());
    }
  }

  private void sendMessageToBot(String message) {
    QueryInput input = QueryInput.newBuilder()
            .setText(TextInput.newBuilder().setText(message).setLanguageCode("en-US")).build();
    new SendMessageInBg(this, sessionName, sessionsClient, input).execute();
  }

  @Override
  public void callback(DetectIntentResponse returnResponse) {
    if(returnResponse!=null) {
      final String botReply = returnResponse.getQueryResult().getFulfillmentText();
      if(!botReply.isEmpty()){
        messageList.add(new Message(botReply, true));
        chatAdapter.notifyDataSetChanged();
        Objects.requireNonNull(chatView.getLayoutManager()).scrollToPosition(messageList.size() - 1);
        // To speak the response from dialogflow
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
          @Override
          public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS) {
              int result = tts.setLanguage(Locale.getDefault());
              tts.speak(botReply, TextToSpeech.QUEUE_FLUSH, null);
              if (result == TextToSpeech.LANG_MISSING_DATA
                      || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported");
              }
            } else {
              Log.e("TTS", "Initialization failed");
            }
          }
        });

      }else {
        Toast.makeText(this, "something went wrong", Toast.LENGTH_SHORT).show();
      }
    } else {
      Toast.makeText(this, "failed to connect!", Toast.LENGTH_SHORT).show();
    }
  }


  @Override
  protected void onDestroy() {
    // when app is exited
    super.onDestroy();
    speechRecognizer.destroy();
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
  }

}