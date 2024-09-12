package com.wingtech.cloudserver.client;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText userName;
    private EditText password;
    private Button login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        userName = (EditText) findViewById(R.id.username);
        password = (EditText) findViewById(R.id.password);
        login = (Button) findViewById(R.id.btn_login);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String uName = userName.getText().toString();
                String pword = password.getText().toString();
                SharedPreferences prefs = getSharedPreferences("userdata",MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("username",uName);
                editor.putString("pword",pword);
                editor.commit();
                Log.d(TAG, "onClick: username=" + uName);
                Intent intent = new Intent(LoginActivity.this,LeadActivity.class);
                intent.putExtra("username",uName);
                startActivity(intent);
            }
        });
    }
}
