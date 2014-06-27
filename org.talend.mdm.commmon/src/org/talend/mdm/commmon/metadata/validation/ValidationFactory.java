/*
<<<<<<< HEAD:org.talend.mdm.commmon/src/org/talend/mdm/commmon/metadata/validation/ValidationFactory.java
 * Copyright (C) 2006-2013 Talend Inc. - www.talend.com
 *
=======
 * Copyright (C) 2006-2014 Talend Inc. - www.talend.com
 * 
>>>>>>> e100b9c... TMDM-7482: "view contains complex type fields throw error in webUI":main/plugins/org.talend.mdm.commmon/src/org/talend/mdm/commmon/metadata/validation/ValidationFactory.java
 * This source code is available under agreement available at
 * %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
 * 
 * You should have received a copy of the agreement along with this program; if not, write to Talend SA 9 rue Pages
 * 92150 Suresnes, France
 */

package org.talend.mdm.commmon.metadata.validation;

import org.apache.commons.lang.BooleanUtils;
import org.talend.mdm.commmon.metadata.*;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.BooleanUtils;
import org.talend.mdm.commmon.metadata.*;

public class ValidationFactory {

    private static final String VALIDATION_MARKER = "validation.validated"; //$NON-NLS-1$

    private static boolean isValidated(MetadataExtensible metadataElement) {
        return BooleanUtils.isTrue(metadataElement.<Boolean> getData(VALIDATION_MARKER));
    }

    public static ValidationRule getRule(FieldMetadata field) {
        if (isValidated(field)) {
            if (field instanceof UnresolvedFieldMetadata) {
                return NoOpValidationRule.FAIL;
            } else {
                return NoOpValidationRule.SUCCESS;
            }
        }
        field.setData(VALIDATION_MARKER, true);
        return field.createValidationRule();
    }

    public static ValidationRule getRule(SoftFieldRef field) {
        throw new IllegalArgumentException("Soft references must be frozen before validation.");
    }

    public static ValidationRule getRule(CompoundFieldMetadata field) {
        return new FieldTypeValidationRule(field);
    }

    public static ValidationRule getRule(ReferenceFieldMetadata field) {
        List<ValidationRule> rules = new LinkedList<ValidationRule>();
        rules.add(new FieldTypeValidationRule(field)); // All fields common rule
        rules.add(new ForeignKeyExist(field));
        rules.add(new ForeignKeyMaxLength(field));
        rules.add(new ForeignKeyCannotBeKey(field));
        rules.add(new ForeignKeyNotStringTyped(field));
        rules.add(new ForeignKeyInfo(field));
        // Warning
        rules.add(new ForeignKeyShouldPointToID(field));
        return new CompositeValidationRule(rules.toArray(new ValidationRule[rules.size()]));
    }

    public static ValidationRule getRule(SimpleTypeFieldMetadata field) {
        return new FieldTypeValidationRule(field);
    }

    public static ValidationRule getRule(SoftIdFieldRef field) {
        throw new IllegalArgumentException("Soft references must be frozen before validation.");
    }

    public static ValidationRule getRule(UnresolvedFieldMetadata field) {
        return new UnresolvedField(field);
    }

    public static ValidationRule getRule(ContainedTypeFieldMetadata field) {
        return new FieldTypeValidationRule(field);
    }

    public static ValidationRule getRule(EnumerationFieldMetadata field) {
        return new FieldTypeValidationRule(field);
    }

    public static ValidationRule getRule(TypeMetadata type) {
        if (isValidated(type)) {
            if (type instanceof UnresolvedTypeMetadata) {
                return NoOpValidationRule.FAIL;
            } else {
                return NoOpValidationRule.SUCCESS;
            }
        }
        type.setData(VALIDATION_MARKER, true);
        return type.createValidationRule();
    }

    public static ValidationRule getRule(SoftTypeRef type) {
        throw new IllegalArgumentException("Soft references must be frozen before validation.");
    }

    public static ValidationRule getRule(ComplexTypeMetadataImpl type) {
        List<ValidationRule> rules = new LinkedList<ValidationRule>();
        rules.add(new SuperTypeValidationRule(type));
        rules.add(new KeyFieldsValidationRule(type));
        rules.add(new LookupFieldsValidationRule(type));
        rules.add(new PrimaryKeyInfoValidationRule(type));
        rules.add(new XSDAttributeValidationRule(type));
        return new CompositeValidationRule(rules.toArray(new ValidationRule[rules.size()]));
    }

    public static ValidationRule getRule(UnresolvedTypeMetadata unresolvedType) {
        return new UnresolvedType(unresolvedType);
    }
}
