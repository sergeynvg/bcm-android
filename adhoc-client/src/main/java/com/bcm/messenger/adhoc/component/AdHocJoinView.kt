package com.bcm.messenger.adhoc.component

import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocChannel
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.adhoc.logic.AdHocSessionLogic
import com.bcm.messenger.adhoc.ui.channel.AdHocConversationActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.getDrawable
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import kotlinx.android.synthetic.main.adhoc_join_view.view.*

/**
 * adhoc invite view
 *
 * Created by wjh on 2019/8/19
 */
class AdHocJoinView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val TAG = "AdHocJoinView"
    private var mContent: AmeGroupMessage.AirChatContent? = null
    private var mOutgoing = false
    private var accountContext:AccountContext? = null


    init {
        inflate(context, R.layout.adhoc_join_view, this)
        join_action_tv.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val content = mContent ?: return@setOnClickListener
            val accountContext = this.accountContext ?: return@setOnClickListener
            AdHocSessionLogic.get(accountContext).addChannelSession(content.name, content.password) {
                val accountContext = this.accountContext
                if (it.isNotEmpty() && accountContext != null) {
                    context.startBcmActivity(accountContext, Intent(context, AdHocConversationActivity::class.java).apply {
                        putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, it)
                    })
                }
            }

        }

    }


    /**
     * set name card data
     */
    fun setData(accountContext: AccountContext, content: AmeGroupMessage.AirChatContent, outgoing: Boolean) {
        mContent = content
        mOutgoing = outgoing
        this.accountContext = accountContext

        var name = AdHocChannelLogic.get(accountContext).getChannel(AdHocChannel.cid(content.name, content.password))?.viewName()
        if (name.isNullOrBlank()) {
            name = content.name
        }

        val span = SpannableStringBuilder()
        if (!outgoing) {
            setBackgroundResource(R.drawable.chats_share_card_incoming_bg)
            span.append(StringAppearanceUtil.applyAppearance(name, color = getColor(R.color.common_color_black)))
            join_action_tv.background = getDrawable(R.drawable.chats_group_share_card_incoming_button)
            join_action_tv.setTextColor(getColor(R.color.common_app_primary_color))


        }else {
            setBackgroundResource(R.drawable.chats_share_card_outgoing_bg)
            span.append(StringAppearanceUtil.applyAppearance(name, color = getColor(R.color.common_color_white)))
            join_action_tv.background = getDrawable(R.drawable.chats_group_share_card_outgoing_button)
            join_action_tv.setTextColor(getColor(R.color.common_color_white))
        }

        join_name_tv.text = span
        join_logo_layout.setChannel(content.name, null)

    }

}