package ch.heuscher.safe_home_button

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity

class ImpressumActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_impressum)

        supportActionBar?.hide()

        // Setup back button
        findViewById<Button>(R.id.back_button).setOnClickListener {
            finish()
        }

        // Set version info
        val versionText = findViewById<TextView>(R.id.version_text)
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        versionText.text = "Version ${packageInfo.versionName} (Build ${packageInfo.longVersionCode})"

        // Respect system window insets so content doesn't get covered by nav bar
        val root = findViewById<ScrollView>(R.id.impressum_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            // Add extra bottom padding equal to nav bar inset to keep last content above nav
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, (48 * resources.displayMetrics.density).toInt() + navBarInsets.bottom)
            insets
        }
    }
}