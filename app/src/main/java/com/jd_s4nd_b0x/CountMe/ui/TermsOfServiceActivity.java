package com.jd_s4nd_b0x.CountMe.ui;

import com.jd_s4nd_b0x.CountMe.R;

public class TermsOfServiceActivity extends LegalActivity {
    @Override
    protected int titleRes() {
        return R.string.terms_of_service;
    }

    @Override
    protected String assetName() {
        return "terms-of-service.html";
    }
}
