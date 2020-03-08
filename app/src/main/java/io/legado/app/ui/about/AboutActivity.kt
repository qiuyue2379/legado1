package io.legado.app.ui.about

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.openUrl
import kotlinx.android.synthetic.main.activity_about.*
import org.jetbrains.anko.share


class AboutActivity : BaseActivity(R.layout.activity_about) {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val fTag = "aboutFragment"
        var aboutFragment = supportFragmentManager.findFragmentByTag(fTag)
        if (aboutFragment == null) aboutFragment = AboutFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fl_fragment, aboutFragment, fTag)
            .commit()
        tv_app_summary.post {
            val span = ForegroundColorSpan(accentColor)
            val spannableString = SpannableString(tv_app_summary.text)
            val start = spannableString.indexOf("开源阅读软件")
            spannableString.setSpan(
                span, start, start + 6,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            tv_app_summary.text = spannableString
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.about, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scoring -> openUrl("market://details?id=$packageName")
            R.id.menu_share_it -> share(
                getString(R.string.app_share_description),
                getString(R.string.app_name)
            )
        }
        return super.onCompatOptionsItemSelected(item)
    }

}
