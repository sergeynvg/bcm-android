package com.bcm.messenger.me.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.utility.QuickOpCheck
import kotlinx.android.synthetic.main.me_fragment_ban_reset_password.*

/**

 * Created by zjl on 2018/9/7.
 */
class BanResetPasswordFragment : AbsRegistrationFragment() {

    private val TAG = "BanResetPasswordFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_ban_reset_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ban_reset_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {

            override fun onClickLeft() {
                activity?.apply { supportFragmentManager.popBackStack() }

            }
            override fun onClickRight() {
                AmeModuleCenter.user(accountContext)?.gotoBackupTutorial()
            }
        })


        ban_reset_back_btn.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            activity?.apply { supportFragmentManager.popBackStack() }

//            AmePopup.tipLoading.show(activity)
//            Observable.create(ObservableOnSubscribe<ECKeyPair> {
//                try {
//                    it.onNext(IdentityKeyUtil.generateIdentityKeys(context))
//                } finally {
//                    it.onComplete()
//                }
//            }).subscribeOn(Schedulers.computation())
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe({
//                        keyPair = it
//                        if (keyPair != null) {
//                            AmeLoginLogic.queryChallengeTarget(it) { target ->
//
//                                AmePopup.tipLoading.dismiss()
//                                if (target != null && activity != null) {
//                                    val f = GenerateKeyFragment2()
//                                    val arg = Bundle()
//                                    arg.putInt("action", 1)
//                                    arg.putString("target", target)
//                                    f.arguments = arg
//                                    activity!!.supportFragmentManager.beginTransaction()
//                                            .replace(R.id.register_container, f, "generate_key_fragment")
//                                            .addToBackStack("ban_reset_password_fragment")
//                                            .commitAllowingStateLoss()
//                                    f.setKeyPair(it)
//                                }
//                            }
//                        } else {
//                            AmePopup.tipLoading.dismiss()
//                        }
//                    }, {
//                        AmePopup.tipLoading.dismiss()
//                        ALog.e(TAG, it.toString())
//                    })
        }

        activity?.window?.setStatusBarLightMode()
    }


}