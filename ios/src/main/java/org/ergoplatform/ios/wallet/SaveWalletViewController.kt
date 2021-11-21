package org.ergoplatform.ios.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.ergoplatform.api.AesEncryptionManager
import org.ergoplatform.appkit.SecretString
import org.ergoplatform.ios.ui.*
import org.ergoplatform.persistance.ENC_TYPE_PASSWORD
import org.ergoplatform.serializeSecrets
import org.ergoplatform.uilogic.*
import org.ergoplatform.uilogic.wallet.SaveWalletUiLogic
import org.robovm.apple.foundation.NSArray
import org.robovm.apple.uikit.*

class SaveWalletViewController(private val mnemonic: SecretString) : CoroutineViewController() {
    private lateinit var progressIndicator: UIActivityIndicatorView
    private lateinit var scrollView: UIScrollView
    private lateinit var addressLabel: UILabel
    private val uiLogic = SaveWalletUiLogic(mnemonic)

    override fun viewDidLoad() {
        super.viewDidLoad()

        val texts = getAppDelegate().texts
        title = texts.get(STRING_TITLE_WALLET_DETAILS)
        view.backgroundColor = UIColor.systemBackground()

        navigationController.navigationBar?.tintColor = uiColorErgo

        val container = UIView()
        scrollView = container.wrapInVerticalScrollView()
        view.addSubview(scrollView)
        scrollView.edgesToSuperview()

        val introLabel = Body1Label()
        introLabel.text = texts.get(STRING_INTRO_SAVE_WALLET)

        addressLabel = Headline2Label()

        val addressInfoLabel = Body1Label()
        addressInfoLabel.text = texts.get(STRING_INTRO_SAVE_WALLET2)

        val buttonSavePassword = TextButton(texts.get(STRING_BUTTON_SAVE_PASSWORD_ENCRYPTED))
        buttonSavePassword.addOnTouchUpInsideListener { _, _ ->
            PasswordViewController.showDialog(
                this@SaveWalletViewController,
                { password ->
                    if (uiLogic.isPasswordWeak(password)) {
                        return@showDialog texts.get(STRING_ERR_PASSWORD)
                    }

                    val encrypted = AesEncryptionManager.encryptData(
                        password,
                        serializeSecrets(mnemonic.toStringUnsecure()).toByteArray()
                    )

                    saveToDbAndDismissController(ENC_TYPE_PASSWORD, encrypted)
                    return@showDialog null
                }, true
            )
        }

        val savePwInfoLabel =
            Body1Label().apply { text = texts.get(STRING_DESC_SAVE_PASSWORD_ENCRYPTED) }

        val buttonSaveDevice = TextButton(texts.get(STRING_BUTTON_SAVE_DEVICE_ENCRYPTED))
        buttonSaveDevice.isEnabled = false
        val saveDeviceEncInfo =
            Body1Label().apply { text = texts.format(STRING_DESC_SAVE_DEVICE_ENCRYPTED, "n/a") }

        val addressInfoStack = UIStackView(
            NSArray(
                introLabel,
                addressLabel,
                addressInfoLabel,
                createHorizontalSeparator(),
                buttonSavePassword,
                savePwInfoLabel,
                createHorizontalSeparator(),
                buttonSaveDevice,
                saveDeviceEncInfo
            )
        )
        addressInfoStack.axis = UILayoutConstraintAxis.Vertical
        addressInfoStack.spacing = DEFAULT_MARGIN * 2
        addressInfoStack.arrangedSubviews.forEach { it.widthMatchesSuperview() }
        container.addSubview(addressInfoStack)
        addressInfoStack.setCustomSpacing(DEFAULT_MARGIN * 4, addressInfoLabel)
        addressInfoStack.topToSuperview(false, DEFAULT_MARGIN * 2)
            .widthMatchesSuperview(inset = DEFAULT_MARGIN, maxWidth = MAX_WIDTH).bottomToSuperview()

        container.addSubview(addressInfoStack)

        progressIndicator = UIActivityIndicatorView()
        progressIndicator.activityIndicatorViewStyle = UIActivityIndicatorViewStyle.Large
        view.addSubview(progressIndicator)
        progressIndicator.centerVertical().centerHorizontal()
    }

    private fun saveToDbAndDismissController(encType: Int, secretStorage: ByteArray) {
        GlobalScope.launch(Dispatchers.IO) {
            val appDelegate = getAppDelegate()
            uiLogic.suspendSaveToDb(
                appDelegate.database, IosStringProvider(appDelegate.texts),
                encType, secretStorage
            )
        }
        navigationController.dismissViewController(true) {}
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)
        calculateAndShowPkAddress()
    }

    private fun calculateAndShowPkAddress() {
        scrollView.isHidden = true
        progressIndicator.startAnimating()

        viewControllerScope.launch {
            val publicErgoAddressFromMnemonic = uiLogic.publicAddress
            runOnMainThread {
                addressLabel.text = publicErgoAddressFromMnemonic
                progressIndicator.isHidden = true
                scrollView.isHidden = false
            }
        }
    }
}