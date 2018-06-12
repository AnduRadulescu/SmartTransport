package com.example.alexa.smarttransport;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private Button driver,customer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        driver = findViewById(R.id.driver);
        customer = findViewById(R.id.customer);

        startService(new Intent(MainActivity.this, OnAppKilled.class));
        driver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               Intent intent = new Intent (MainActivity.this, DriverLoginActivity.class);
               startActivity(intent);
               finish();
               return;
            }
        });
        customer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent (MainActivity.this, CustomerActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
    }
}
