package com.jd_s4nd_b0x.CountMe.ui;

import com.jd_s4nd_b0x.CountMe.R;

public class PrivacyPolicyActivity extends LegalActivity {
    @Override
    protected int titleRes() {
        return R.string.privacy_policy;
    }

    @Override
    protected String assetName() {
        return "privacy-policy.html";
    }
}
