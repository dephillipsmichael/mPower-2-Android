package org.sagebionetworks.research.mpower.researchstack.framework.step;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.step.Step;
import org.researchstack.backbone.utils.ObservableUtils;
import org.sagebionetworks.bridge.rest.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.rest.model.Phone;
import org.sagebionetworks.research.mpower.researchstack.R;
import org.sagebionetworks.research.mpower.researchstack.framework.MpDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Subscription;

public class MpPhoneInstructionStepLayout extends MpInstructionStepLayout {

    private static final Logger LOGGER = LoggerFactory.getLogger(MpPhoneInstructionStepLayout.class);

    protected EditText phoneEntryField;

    private MpPhoneInstructionStep mpPhoneInstructionStep;

    public MpPhoneInstructionStepLayout(Context context) {
        super(context);
    }

    public MpPhoneInstructionStepLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MpPhoneInstructionStepLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MpPhoneInstructionStepLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void goForwardClicked(View v) {
        MpDataProvider provider = MpDataProvider.getInstance();
        String phoneNumber = phoneEntryField.getText().toString();
        Phone phone = new Phone().number(phoneNumber).regionCode("US");

        findViewById(R.id.button_go_forward)
                .setEnabled(false);

        Subscription response = provider.signUp(phone)
                .compose(ObservableUtils.applyDefault())
                .subscribe(dataResponse -> {
                    LOGGER.debug("Successfully signed up!");
                    super.goForwardClicked(v);
                }, throwable -> {
                    LOGGER.warn("Sign Up error ", throwable);

                    // 400 is the response for an invalid phone number
                    if (throwable instanceof InvalidEntityException) {
                        showOkAlertDialog(
                                "The phone number you entered is not valid. Please enter a valid U.S. phone number.");
                    } else {
                        findViewById(R.id.button_go_forward)
                                .setEnabled(true);
                        showOkAlertDialog("The server returned an error: \n" + throwable.getMessage());
                    }
                });
    }

    @Override
    public void initialize(Step step, StepResult result) {
        validateAndSetMpStartTaskStep(step);
        super.initialize(step, result);

        // for internal builds
        View v = findViewById(R.id.internal_sign_in_link);
        if (v != null) {
            v.setOnClickListener(this::onInternalSignInClick);
        }

        phoneEntryField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
                // no-op
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                //no-op
            }

            @Override
            public void afterTextChanged(final Editable s) {
                if (phoneEntryField.getText().length() == 10) {
                    findViewById(R.id.button_go_forward)
                            .setEnabled(true);
                } else {
                    findViewById(R.id.button_go_forward)
                            .setEnabled(false);
                }
            }
        });
    }

    void onInternalSignInClick(View v) {
        Intent i = new Intent();
        // Fragile String-based reference, but this is used for internal builds (testing) only
        i.setClassName(getContext(), "org.sagebionetworks.research.mpower.authentication.ExternalIdSignInActivity");
        getContext().startActivity(i);
    }

    @Override
    public int getContentResourceId() {
        return R.layout.mp_step_layout_phone_entry;
    }

    @Override
    public void connectStepUi(int titleRId, int textRId, int imageRId, int detailRId) {
        super.connectStepUi(titleRId, textRId, imageRId, detailRId);
        findViewById(R.id.button_go_forward)
                .setEnabled(false);
        phoneEntryField = findViewById(R.id.mp_entry_field);
    }


    @Override
    public void refreshStep() {
        super.refreshStep();

        phoneEntryField.setHint("Enter your mobile number");
        // TODO figure out back button layout issue
//        backButton.setVisibility(VISIBLE);

        nextButton.setOnClickListener(this::goForwardClicked);
    }

    protected void validateAndSetMpStartTaskStep(Step step) {
        if (!(step instanceof MpPhoneInstructionStep)) {
            throw new IllegalStateException("MpPhoneInstructionStepLayout only works with MpPhoneInstructionStep");
        }
        this.mpPhoneInstructionStep = (MpPhoneInstructionStep) step;
    }
}
