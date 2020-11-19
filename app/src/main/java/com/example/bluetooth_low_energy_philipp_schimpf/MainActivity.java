package com.example.bluetooth_low_energy_philipp_schimpf;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private Button ClientBtn,ServerBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ClientBtn = (Button) findViewById(R.id.gattClientButton);
        ServerBtn = (Button) findViewById(R.id.gattServerButton);

        ClientBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this,
                GattClientActivity.class)));

        ServerBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this,
                GattServerActivity.class)));
    }
}