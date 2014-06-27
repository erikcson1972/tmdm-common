/*
<<<<<<< HEAD:org.talend.mdm.commmon/src/org/talend/mdm/commmon/metadata/validation/CompositeValidationRule.java
 * Copyright (C) 2006-2013 Talend Inc. - www.talend.com
 *
=======
 * Copyright (C) 2006-2014 Talend Inc. - www.talend.com
 * 
>>>>>>> e100b9c... TMDM-7482: "view contains complex type fields throw error in webUI":main/plugins/org.talend.mdm.commmon/src/org/talend/mdm/commmon/metadata/validation/CompositeValidationRule.java
 * This source code is available under agreement available at
 * %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
 * 
 * You should have received a copy of the agreement along with this program; if not, write to Talend SA 9 rue Pages
 * 92150 Suresnes, France
 */

package org.talend.mdm.commmon.metadata.validation;

import org.talend.mdm.commmon.metadata.ValidationHandler;

/**
 * Groups several {@link org.talend.mdm.commmon.metadata.validation.ValidationRule rules} into a single
 * {@link org.talend.mdm.commmon.metadata.validation.ValidationRule} implementation.
 */
public class CompositeValidationRule implements ValidationRule {

    private final ValidationRule[] rules;

    public CompositeValidationRule(ValidationRule... rules) {
        this.rules = rules;
    }

    @Override
    public boolean perform(ValidationHandler handler) {
        boolean allSucceed = true;
        for (ValidationRule rule : rules) {
            boolean succeeded = rule.perform(handler);
            allSucceed &= succeeded;
            if (!succeeded && !rule.continueOnFail()) {
                break;
            }
        }
        return allSucceed;
    }

    @Override
    public boolean continueOnFail() {
        boolean allContinueOnFail = true;
        for (ValidationRule rule : rules) {
            allContinueOnFail &= rule.continueOnFail();
        }
        return allContinueOnFail;
    }
}
