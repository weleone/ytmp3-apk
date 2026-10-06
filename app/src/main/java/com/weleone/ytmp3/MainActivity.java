package com.weleone.ytmp3;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            Fragment f = item.getItemId() == R.id.nav_server
                    ? new ServerFragment() : new LocalFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, f).commit();
            return true;
        });
        nav.setSelectedItemId(R.id.nav_local);

        // 右上角风格切换
        findViewById(R.id.btn_theme).setOnClickListener(v -> {
            ThemeHelper.toggle(this);
            Toast.makeText(this, ThemeHelper.isMaterialYou(this)
                    ? "已切换：新安卓配色" : "已切换：默认风格",
                    Toast.LENGTH_SHORT).show();
            recreate();
        });

        // 固定页脚版本号
        TextView tvVersion = findViewById(R.id.tv_version);
        try {
            String ver = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText("v" + ver);
        } catch (Exception ignored) {
        }
    }
}
