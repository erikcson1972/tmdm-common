/*
 * Copyright (C) 2006-2013 Talend Inc. - www.talend.com
 *
 * This source code is available under agreement available at
 * %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
 *
 * You should have received a copy of the agreement
 * along with this program; if not, write to Talend SA
 * 9 rue Pages 92150 Suresnes, France
 */

package org.talend.mdm.commmon.metadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class SoftFieldRef implements FieldMetadata {

    private final MetadataRepository repository;

    private final SoftFieldRef containingField;

    private final TypeMetadata containingType;

    private final String fieldName;

    private final Map<String, Object> additionalData = new HashMap<String, Object>();

    private FieldMetadata frozenField;

    public SoftFieldRef(MetadataRepository metadataRepository, String fieldName, TypeMetadata containingType) {
        this.repository = metadataRepository;
        this.containingType = containingType;
        this.fieldName = fieldName;
        this.containingField = null;
    }

    public SoftFieldRef(MetadataRepository metadataRepository, String fieldName, SoftFieldRef containingField) {
        this.repository = metadataRepository;
        this.containingField = containingField;
        this.containingType = null;
        this.fieldName = fieldName;
    }

    private FieldMetadata getField() {
        if (frozenField == null) {
            throw new IllegalStateException("Field reference should be frozen before used.");
        }
        return frozenField;
    }

    @Override
    public synchronized void setData(String key, Object data) {
        additionalData.put(key, data);
    }

    @Override
    public <X> X getData(String key) {
        return (X) additionalData.get(key);
    }

    @Override
    public String getName() {
        return fieldName;
    }

    @Override
    public boolean isKey() {
        return getField().isKey();
    }

    @Override
    public TypeMetadata getType() {
        return getField().getType();
    }

    @Override
    public ComplexTypeMetadata getContainingType() {
        return getField().getContainingType();
    }

    @Override
    public void setContainingType(ComplexTypeMetadata typeMetadata) {
        getField().setContainingType(typeMetadata);
    }

    @Override
    public FieldMetadata freeze(ValidationHandler handler) {
        if (frozenField != null) {
            return frozenField;
        }
        if (containingType != null) {
            ComplexTypeMetadata type = repository.getComplexType(containingType.getName());
            Integer lineNumberObject = (Integer) additionalData.get(MetadataRepository.XSD_LINE_NUMBER);
            Integer columnNumberObject = (Integer) additionalData.get(MetadataRepository.XSD_COLUMN_NUMBER);
            if (type == null) {
                handler.error(null,
                        "Type '" + containingType + "' does not exist.",
                        -1,
                        -1);
                return this;
            }
            if (!type.hasField(fieldName)) {
                handler.error(type,
                        "Type '" + containingType + "' does not own field '" + fieldName + "'.",
                        lineNumberObject == null ? containingType.<Integer>getData(MetadataRepository.XSD_LINE_NUMBER) : lineNumberObject,
                        columnNumberObject == null ? containingType.<Integer>getData(MetadataRepository.XSD_COLUMN_NUMBER) : columnNumberObject);
                return this;
            }
            frozenField = type.getField(fieldName);
        } else {
            frozenField = containingField;
        }
        Set<Map.Entry<String,Object>> data = additionalData.entrySet();
        for (Map.Entry<String, Object> currentData : data) {
            frozenField.setData(currentData.getKey(), currentData.getValue());
        }
        return frozenField;
    }

    @Override
    public void promoteToKey() {
        getField().promoteToKey();
    }

    @Override
    public TypeMetadata getDeclaringType() {
        return getField().getDeclaringType();
    }

    @Override
    public void adopt(ComplexTypeMetadata metadata, MetadataRepository repository) {
        FieldMetadata copy = getField().copy(this.repository);
        copy.setContainingType(metadata);
        metadata.addField(copy);
    }

    @Override
    public FieldMetadata copy(MetadataRepository repository) {
        if (containingType == null) {
            return new SoftFieldRef(repository, fieldName, containingField);
        } else {
            return new SoftFieldRef(repository, fieldName, containingType.copy(repository));
        }
    }

    @Override
    public List<String> getHideUsers() {
        return getField().getHideUsers();
    }

    @Override
    public List<String> getWriteUsers() {
        return getField().getWriteUsers();
    }

    @Override
    public boolean isMany() {
        return getField().isMany();
    }

    @Override
    public boolean isMandatory() {
        return getField().isMandatory();
    }

    @Override
    public <T> T accept(MetadataVisitor<T> visitor) {
        return getField().accept(visitor);
    }

    @Override
    public String toString() {
        if (containingType != null) {
            return containingType.toString() + "/" + fieldName; //$NON-NLS-1$
        } else {
            return containingField.toString();
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof FieldMetadata && getField().equals(o);
    }
}
