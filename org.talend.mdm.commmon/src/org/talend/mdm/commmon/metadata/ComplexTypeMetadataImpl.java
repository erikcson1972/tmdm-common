/*
 * Copyright (C) 2006-2013 Talend Inc. - www.talend.com
 * 
 * This source code is available under agreement available at
 * %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
 * 
 * You should have received a copy of the agreement along with this program; if not, write to Talend SA 9 rue Pages
 * 92150 Suresnes, France
 */

package org.talend.mdm.commmon.metadata;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.talend.mdm.commmon.metadata.validation.ValidationFactory;
import org.talend.mdm.commmon.metadata.validation.ValidationRule;

/**
 * Default implementation for a MDM entity type (i.e. "complex" type).
 */
public class ComplexTypeMetadataImpl extends MetadataExtensions implements ComplexTypeMetadata {

    private static final Logger LOGGER = Logger.getLogger(ComplexTypeMetadataImpl.class);

    private final String nameSpace;

    private final List<String> allowWrite;

    private final Map<String, FieldMetadata> fieldMetadata = new LinkedHashMap<String, FieldMetadata>();

    private final Map<String, FieldMetadata> keyFields = new LinkedHashMap<String, FieldMetadata>();

    private final Collection<TypeMetadata> superTypes = new HashSet<TypeMetadata>();

    private final List<String> denyCreate;

    private final List<String> hideUsers;

    private final List<String> logicalDelete;

    private final String schematron;

    private final List<String> physicalDelete;

    private final Collection<ComplexTypeMetadata> subTypes = new HashSet<ComplexTypeMetadata>();

    private List<FieldMetadata> lookupFields;

    private boolean isInstantiable;

    private List<FieldMetadata> primaryKeyInfo;

    private String name;

    private boolean isFrozen;

    private final List<String> workflowAccessRights;

    private FieldMetadata containingField;

    private final Set<ComplexTypeMetadata> usages = new HashSet<ComplexTypeMetadata>();

    public ComplexTypeMetadataImpl(String nameSpace, String name, boolean instantiable) {
        this(nameSpace, name, Collections.<String> emptyList(), Collections.<String> emptyList(), Collections
                .<String> emptyList(), Collections.<String> emptyList(), Collections.<String> emptyList(), StringUtils.EMPTY,
                Collections.<FieldMetadata> emptyList(), Collections.<FieldMetadata> emptyList(), instantiable, Collections
                        .<String> emptyList());
    }

    public ComplexTypeMetadataImpl(String nameSpace, String name, List<String> allowWrite, List<String> denyCreate,
            List<String> hideUsers, List<String> physicalDelete, List<String> logicalDelete, String schematron,
            List<FieldMetadata> primaryKeyInfo, List<FieldMetadata> lookupFields, boolean instantiable,
            List<String> workflowAccessRights) {
        this.name = name;
        this.nameSpace = nameSpace;
        this.allowWrite = allowWrite;
        this.denyCreate = denyCreate;
        this.hideUsers = hideUsers;
        this.physicalDelete = physicalDelete;
        this.logicalDelete = logicalDelete;
        this.schematron = schematron;
        this.primaryKeyInfo = primaryKeyInfo;
        this.lookupFields = lookupFields;
        this.isInstantiable = instantiable;
        this.workflowAccessRights = workflowAccessRights;
    }

    @Override
    public void addSuperType(TypeMetadata superType) {
        if (isFrozen) {
            throw new IllegalStateException("Type '" + name + "' is frozen and can not be modified.");
        }
        superTypes.add(superType);
    }

    @Override
    public Collection<TypeMetadata> getSuperTypes() {
        return Collections.unmodifiableCollection(superTypes);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        if (isFrozen) {
            throw new IllegalStateException("Cannot change name after type was frozen.");
        }
        this.name = name;
    }

    @Override
    public String getNamespace() {
        return nameSpace;
    }

    @Override
    public FieldMetadata getField(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("Field name can not be null nor empty.");
        }
        if (fieldName.indexOf('/') < 0) {
            return fieldMetadata.get(fieldName); // Shortcut for direct look up for a field (no hierarchy involved).
        } else {
            StringTokenizer tokenizer = new StringTokenizer(fieldName, "/"); //$NON-NLS-1$
            // think about reusable type, e.g: Employee/Address[@xsi:type="CNAddressType"]/Province
            ComplexTypeMetadata currentType = this;
            FieldMetadata currentField = null;
            while (tokenizer.hasMoreTokens()) {
                String nextToken = tokenizer.nextToken();
                String currentFieldName = StringUtils.substringBefore(nextToken, "["); //$NON-NLS-1$
                // Handle xsi:type in XPath query
                if (nextToken.contains("xsi:type")) { //$NON-NLS-1$
                    String reusableTypeName = StringUtils
                            .substringAfter(nextToken, "@xsi:type").replace('=', ' ').replace(']', ' ').trim(); //$NON-NLS-1$
                    if (reusableTypeName.isEmpty()) {
                        throw new IllegalArgumentException("Reusable type could not be null for fieldName '" + fieldName + "'"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                    if (!reusableTypeName.isEmpty() && !currentType.getName().equals(reusableTypeName)) { // Look real
                                                                                                          // type in sub
                                                                                                          // types
                        boolean foundRealType = false;
                        for (TypeMetadata subType : currentType.getSubTypes()) {
                            if (subType instanceof ComplexTypeMetadata && subType.getName().equals(reusableTypeName)) {
                                currentType = (ComplexTypeMetadata) subType;
                                foundRealType = true;
                                break;
                            }
                        }
                        // xsi:type not found, assume type is default field type.
                        if (!foundRealType) {
                            LOGGER.error("Type '" + reusableTypeName + "' does not exist. Assuming '" + currentType.getName()
                                    + "' has field type.");
                        }
                    }
                }
                // Find field in current type (or sub types, since query might refer to a field not declared in type)
                if (!currentType.hasField(currentFieldName)) {
                    boolean foundInSubTypes = false;
                    for (ComplexTypeMetadata subType : currentType.getSubTypes()) {
                        if (subType.hasField(currentFieldName)) {
                            currentField = subType.getField(currentFieldName);
                            foundInSubTypes = true;
                            break;
                        }
                    }
                    if (!foundInSubTypes) {
                        throw new IllegalArgumentException("Type '" + getName() + "' does not own field '" + fieldName
                                + "' (could not find '" + currentFieldName + "').");
                    }
                } else {
                    currentField = currentType.getField(currentFieldName);
                }
                // Move to the next type (if any element to process).
                if (tokenizer.hasMoreTokens()) {
                    currentType = (ComplexTypeMetadata) currentField.getType();
                }
            }
            return currentField;
        }
    }

    @Override
    public boolean isInstantiable() {
        return isInstantiable;
    }

    @Override
    public boolean isFrozen() {
        return isFrozen;
    }

    @Override
    public void validate(ValidationHandler handler) {
        ValidationFactory.getRule(this).perform(handler);
        // Validate all fields.
        for (FieldMetadata value : fieldMetadata.values()) {
            ValidationFactory.getRule(value).perform(handler);
        }
    }

    @Override
    public ValidationRule createValidationRule() {
        return ValidationFactory.getRule(this);
    }

    @Override
    public void setInstantiable(boolean isInstantiable) {
        if (isFrozen) {
            throw new IllegalStateException("Type '" + name + "' is frozen and can not be modified.");
        }
        this.isInstantiable = isInstantiable;
    }

    @Override
    public ComplexTypeMetadata getEntity() {
        if (getContainer() == null) {
            return this;
        } else {
            return getContainer().getContainingType().getEntity();
        }
    }

    @Override
    public FieldMetadata getContainer() {
        return containingField;
    }

    @Override
    public void setContainer(FieldMetadata field) {
        this.containingField = field;
        for (ComplexTypeMetadata subType : subTypes) {
            subType.setContainer(field);
        }
    }

    @Override
    public Collection<FieldMetadata> getKeyFields() {
        return Collections.unmodifiableCollection(keyFields.values());
    }

    @Override
    public Collection<FieldMetadata> getFields() {
        return Collections.unmodifiableCollection(fieldMetadata.values());
    }

    @Override
    public boolean isAssignableFrom(TypeMetadata type) {
        // Check one level of inheritance
        Collection<TypeMetadata> superTypes = getSuperTypes();
        for (TypeMetadata superType : superTypes) {
            if (type.getName().equals(superType.getName())) {
                return true;
            }
        }
        // Checks in type inheritance hierarchy.
        for (TypeMetadata superType : superTypes) {
            if (superType.isAssignableFrom(type)) {
                return true;
            }
        }
        return getName().equals(type.getName());
    }

    @Override
    public <T> T accept(MetadataVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        if (nameSpace != null && nameSpace.isEmpty()) {
            return '[' + name + ']';
        }
        return '[' + nameSpace + ':' + name + ']';
    }

    @Override
    public void addField(FieldMetadata fieldMetadata) {
        if (isFrozen) {
            throw new IllegalStateException("Type '" + name + "' is frozen and can not be modified.");
        }
        if (fieldMetadata == null) {
            throw new IllegalArgumentException("Field can not be null.");
        }
        this.fieldMetadata.put(fieldMetadata.getName(), fieldMetadata);
        if (fieldMetadata.isKey()) {
            registerKey(fieldMetadata);
        }
    }

    @Override
    public void registerKey(FieldMetadata keyField) {
        if (keyField == null) {
            throw new IllegalArgumentException("Key field can not be null.");
        }
        keyFields.put(keyField.getName(), keyField);
    }

    @Override
    public ComplexTypeMetadata copy() {
        ComplexTypeMetadataImpl copy = new ComplexTypeMetadataImpl(getNamespace(), getName(), allowWrite, denyCreate, hideUsers,
                physicalDelete, logicalDelete, schematron, primaryKeyInfo, Collections.<FieldMetadata> emptyList(),
                isInstantiable, workflowAccessRights);
        Collection<FieldMetadata> fields = getFields();
        for (FieldMetadata field : fields) {
            FieldMetadata fieldCopy = field.copy();
            fieldCopy.setContainingType(copy);
            copy.addField(fieldCopy);
        }
        for (TypeMetadata superType : superTypes) {
            copy.addSuperType(superType);
            if (superType instanceof ComplexTypeMetadata) {
                ((ComplexTypeMetadata) superType).registerSubType(copy);
            }
        }
        for (ComplexTypeMetadata subType : subTypes) {
            copy.subTypes.add((ComplexTypeMetadata) subType.copy());
        }
        Collection<FieldMetadata> typeKeyFields = getKeyFields();
        for (FieldMetadata typeKeyField : typeKeyFields) {
            FieldMetadata fieldCopy = typeKeyField.copy();
            fieldCopy.setContainingType(copy);
            copy.registerKey(fieldCopy);
        }
        copy.isFrozen = false;
        if (dataMap != null) {
            copy.dataMap = new HashMap<String, Object>(dataMap);
        }
        copy.usages.addAll(usages);
        return copy;
    }

    @Override
    public TypeMetadata copyShallow() {
        ComplexTypeMetadataImpl copy = new ComplexTypeMetadataImpl(getNamespace(), getName(), allowWrite, denyCreate, hideUsers,
                physicalDelete, logicalDelete, schematron, primaryKeyInfo, Collections.<FieldMetadata> emptyList(),
                isInstantiable, workflowAccessRights);
        return copy;
    }

    @Override
    public List<String> getWriteUsers() {
        return allowWrite;
    }

    @Override
    public List<String> getDenyCreate() {
        return denyCreate;
    }

    @Override
    public List<String> getHideUsers() {
        return hideUsers;
    }

    @Override
    public List<String> getWorkflowAccessRights() {
        return this.workflowAccessRights;
    }

    @Override
    public List<String> getDenyDelete(DeleteType type) {
        switch (type) {
        case LOGICAL:
            return logicalDelete;
        case PHYSICAL:
            return physicalDelete;
        default:
            throw new NotImplementedException("Security information parsing for delete type '" + type + "'");
        }
    }

    @Override
    public String getSchematron() {
        return schematron;
    }

    @Override
    public List<FieldMetadata> getPrimaryKeyInfo() {
        return primaryKeyInfo;
    }

    @Override
    public List<FieldMetadata> getLookupFields() {
        return lookupFields;
    }

    @Override
    public void declareUsage(ComplexTypeMetadata usage) {
        usages.add(usage);
    }

    @Override
    public Collection<ComplexTypeMetadata> getUsages() {
        return usages;
    }

    @Override
    public void setSubTypes(List<ComplexTypeMetadata> subTypes) {
        this.subTypes.clear();
        this.subTypes.addAll(subTypes);
    }

    @Override
    public boolean hasField(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false;
        }
        if (fieldName.indexOf('/') < 0) {
            boolean contains = fieldMetadata.containsKey(fieldName);
            if (!contains) {
                for (TypeMetadata typeMetadata : getSuperTypes()) {
                    contains |= ((ComplexTypeMetadata) typeMetadata).hasField(fieldName);
                }
            }
            return contains;
        }
        StringTokenizer tokenizer = new StringTokenizer(fieldName, "/"); //$NON-NLS-1$
        ComplexTypeMetadata currentType = this;
        while (tokenizer.hasMoreTokens()) {
            String current = tokenizer.nextToken();
            if (!currentType.hasField(current)) {
                return false;
            }
            if (tokenizer.hasMoreTokens()) {
                TypeMetadata type = currentType.getField(current).getType();
                if (!(type instanceof ComplexTypeMetadata)) {
                    return false;
                }
                currentType = (ComplexTypeMetadata) type;
            }
        }
        return true;
    }

    @Override
    public Collection<ComplexTypeMetadata> getSubTypes() {
        List<ComplexTypeMetadata> subTypes = new LinkedList<ComplexTypeMetadata>();
        for (ComplexTypeMetadata subType : this.subTypes) {
            subTypes.add(subType);
            subTypes.addAll(subType.getSubTypes());
        }
        return subTypes;
    }

    @Override
    public void registerSubType(ComplexTypeMetadata type) {
        subTypes.add(type);
    }

    @Override
    public TypeMetadata freeze() {
        if (isFrozen) {
            return this;
        }
        // Gets fields from super types.
        if (!superTypes.isEmpty()) {
            Collection<FieldMetadata> thisTypeFields = new LinkedList<FieldMetadata>(fieldMetadata.values());
            fieldMetadata.clear();
            List<TypeMetadata> thisSuperTypes = new LinkedList<TypeMetadata>(superTypes);
            superTypes.clear();
            for (TypeMetadata superType : thisSuperTypes) {
                if (isInstantiable() == superType.isInstantiable()) {
                    superType = superType.freeze();
                    if (superType instanceof ComplexTypeMetadata) {
                        ((ComplexTypeMetadata) superType).registerSubType(this);
                        usages.addAll(((ComplexTypeMetadata) superType).getUsages());
                    }
                    superTypes.add(superType);
                } else {
                    superType = superType.freeze();
                }
                if (superType instanceof ComplexTypeMetadata) {
                    ((ComplexTypeMetadata) superType).registerSubType(this);
                    Collection<FieldMetadata> superTypeFields = ((ComplexTypeMetadata) superType).getFields();
                    for (FieldMetadata superTypeField : superTypeFields) {
                        superTypeField.adopt(this);
                    }
                }
            }
            for (FieldMetadata thisTypeField : thisTypeFields) {
                fieldMetadata.put(thisTypeField.getName(), thisTypeField);
            }
        }
        isFrozen = true;
        // Freeze all fields.
        Collection<FieldMetadata> values = new LinkedList<FieldMetadata>(fieldMetadata.values());
        for (FieldMetadata value : values) {
            FieldMetadata frozenFieldDeclaration = value.freeze();
            fieldMetadata.put(value.getName(), frozenFieldDeclaration);
            if (keyFields.containsKey(value.getName()) && !frozenFieldDeclaration.isKey()) {
                frozenFieldDeclaration.promoteToKey();
            }
        }
        for (Map.Entry<String, FieldMetadata> keyField : keyFields.entrySet()) {
            keyField.setValue(keyField.getValue().freeze());
        }
        // Freeze primary info
        List<FieldMetadata> frozenPrimaryKeyInfo = new LinkedList<FieldMetadata>();
        for (FieldMetadata pkInfo : primaryKeyInfo) {
            FieldMetadata freeze = pkInfo.freeze();
            if (freeze != null) {
                frozenPrimaryKeyInfo.add(freeze);
            }
        }
        primaryKeyInfo = frozenPrimaryKeyInfo;
        // Freeze lookup fields
        List<FieldMetadata> frozenLookupFields = new LinkedList<FieldMetadata>();
        for (FieldMetadata lookupField : lookupFields) {
            FieldMetadata freeze = lookupField.freeze();
            if (freeze != null) {
                frozenLookupFields.add(freeze);
            }
        }
        lookupFields = frozenLookupFields;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComplexTypeMetadata)) {
            return false;
        }
        ComplexTypeMetadata that = (ComplexTypeMetadata) o;
        if (!name.equals(that.getName())) {
            return false;
        }
        if (nameSpace != null ? !nameSpace.equals(that.getNamespace()) : that.getNamespace() != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = nameSpace != null ? nameSpace.hashCode() : 0;
        result = 31 * result + name.hashCode();
        return result;
    }
}
