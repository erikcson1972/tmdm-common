/*
<<<<<<< HEAD:org.talend.mdm.commmon/src/org/talend/mdm/commmon/metadata/validation/ForeignKeyExist.java
 * Copyright (C) 2006-2013 Talend Inc. - www.talend.com
 *
=======
 * Copyright (C) 2006-2014 Talend Inc. - www.talend.com
 * 
>>>>>>> e100b9c... TMDM-7482: "view contains complex type fields throw error in webUI":main/plugins/org.talend.mdm.commmon/src/org/talend/mdm/commmon/metadata/validation/ForeignKeyExist.java
 * This source code is available under agreement available at
 * %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
 * 
 * You should have received a copy of the agreement along with this program; if not, write to Talend SA 9 rue Pages
 * 92150 Suresnes, France
 */

package org.talend.mdm.commmon.metadata.validation;

import org.talend.mdm.commmon.metadata.ReferenceFieldMetadata;
import org.talend.mdm.commmon.metadata.ValidationHandler;

class ForeignKeyExist implements ValidationRule {

    private final ReferenceFieldMetadata field;

    public ForeignKeyExist(ReferenceFieldMetadata field) {
        this.field = field;
    }

    @Override
    public boolean perform(ValidationHandler handler) {
        return ValidationFactory.getRule(field.getReferencedField()).perform(handler)
                && ValidationFactory.getRule(field.getReferencedType()).perform(handler);
    }

    @Override
    public boolean continueOnFail() {
        return false;
    }
}
