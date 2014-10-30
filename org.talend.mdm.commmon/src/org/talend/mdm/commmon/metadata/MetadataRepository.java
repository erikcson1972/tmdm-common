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

import org.talend.mdm.commmon.metadata.annotation.*;
import org.talend.mdm.commmon.metadata.xsd.XSDVisitor;
import org.talend.mdm.commmon.metadata.xsd.XmlSchemaWalker;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xsd.*;
import org.eclipse.xsd.util.XSDParser;
import org.talend.mdm.commmon.metadata.annotation.ForeignKeyProcessor;
import org.talend.mdm.commmon.metadata.annotation.LookupFieldProcessor;
import org.talend.mdm.commmon.metadata.annotation.PrimaryKeyInfoProcessor;
import org.talend.mdm.commmon.metadata.annotation.SchematronProcessor;
import org.talend.mdm.commmon.metadata.annotation.UserAccessProcessor;
import org.talend.mdm.commmon.metadata.annotation.XmlSchemaAnnotationProcessor;
import org.talend.mdm.commmon.metadata.annotation.XmlSchemaAnnotationProcessorState;
import org.talend.mdm.commmon.metadata.validation.ValidationFactory;
import org.talend.mdm.commmon.metadata.xsd.XSDVisitor;
import org.talend.mdm.commmon.metadata.xsd.XmlSchemaWalker;
import org.talend.mdm.commmon.util.core.ICoreConstants;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import java.io.*;
import java.util.*;

/**
 *
 */
public class MetadataRepository implements MetadataVisitable, XSDVisitor, Serializable {

    private static final long serialVersionUID = 239525645052486072L;

    public static final String COMPLEX_TYPE_NAME = "metadata.complex.type.name"; //$NON-NLS-1$

    public static final String DATA_MAX_LENGTH = "metadata.data.length"; //$NON-NLS-1$

    public static final String DATA_ZIPPED = "metadata.zipped"; //$NON-NLS-1$

    public static final String XSD_LINE_NUMBER = "metadata.xsd.line"; //$NON-NLS-1$

    public static final String XSD_COLUMN_NUMBER = "metadata.xsd.column"; //$NON-NLS-1$

    public static final String XSD_DOM_ELEMENT = "metadata.xsd.dom.element"; //$NON-NLS-1$

    public static final String ANONYMOUS_PREFIX = "X_ANONYMOUS";

    private static final Logger LOGGER = Logger.getLogger(MetadataRepository.class);

    private final Map<XSDTypeDefinition, List<ComplexTypeMetadata>> entityTypeUsage = new HashMap<XSDTypeDefinition, List<ComplexTypeMetadata>>() {

        @Override
        public List<ComplexTypeMetadata> get(Object key) {
            List<ComplexTypeMetadata> types = super.get(key);
            if (types == null) {
                types = new LinkedList<ComplexTypeMetadata>();
                super.put((XSDTypeDefinition) key, types);
            }
            return types;
        }
    };

    private final static List<XmlSchemaAnnotationProcessor> XML_ANNOTATIONS_PROCESSORS = Arrays.asList(new ForeignKeyProcessor(),
            new UserAccessProcessor(),
            new SchematronProcessor(),
            new PrimaryKeyInfoProcessor(),
            new LookupFieldProcessor());

    private final static String USER_NAMESPACE = StringUtils.EMPTY;

    private final Map<String, Map<String, TypeMetadata>> entityTypes = new TreeMap<String, Map<String, TypeMetadata>>();

    private final Map<String, Map<String, TypeMetadata>> nonInstantiableTypes = new TreeMap<String, Map<String, TypeMetadata>>();

    private final Stack<ComplexTypeMetadata> currentTypeStack = new Stack<ComplexTypeMetadata>();

    private String targetNamespace;

    private int anonymousCounter = 0;

    public MetadataRepository() {
        NoOpValidationHandler noOpValidationHandler = new NoOpValidationHandler();
        // Load XML Schema types
        InputStream xmlSchemaDef = MetadataRepository.class.getResourceAsStream("XMLSchema.xsd"); //$NON-NLS-1$
        if (xmlSchemaDef == null) {
            throw new IllegalStateException("Could not find XML schema definition.");
        }
        load(xmlSchemaDef, noOpValidationHandler);
        // TMDM-4444: Adds standard Talend types such as UUID.
        InputStream internalTypes = MetadataRepository.class.getResourceAsStream("talend_types.xsd"); //$NON-NLS-1$
        if (internalTypes == null) {
            throw new IllegalStateException("Could not find internal type data model.");
        }
        load(internalTypes, noOpValidationHandler);
    }

    public TypeMetadata getType(String name) {
        return getType(USER_NAMESPACE, name);
    }

    public String getUserNamespace() {
        return USER_NAMESPACE;
    }

    public ComplexTypeMetadata getComplexType(String typeName) {
        if (typeName == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }
        try {
            return (ComplexTypeMetadata) getType(USER_NAMESPACE, typeName.trim());
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Type named '" + typeName + "' is not a complex type.");
        }
    }

    public TypeMetadata getType(String nameSpace, String name) {
        if (nameSpace == null) {
            nameSpace = StringUtils.EMPTY;
        }
        Map<String, TypeMetadata> nameSpaceTypes = entityTypes.get(nameSpace);
        if (nameSpaceTypes == null) {
            return null;
        }
        return nameSpaceTypes.get(name.trim());
    }

    /**
     * @return Returns only {@link ComplexTypeMetadata} types defined in the data model by the MDM user (no types
     *         potentially defined in other name spaces such as the XML schema's one).
     */
    public Collection<ComplexTypeMetadata> getUserComplexTypes() {
        List<ComplexTypeMetadata> complexTypes = new LinkedList<ComplexTypeMetadata>();
        // User types are all located in the default (empty) name space.
        Map<String, TypeMetadata> userNamespace = entityTypes.get(USER_NAMESPACE);
        if (userNamespace == null) {
            return Collections.emptyList();
        }
        Collection<TypeMetadata> namespaceTypes = userNamespace.values();
        for (TypeMetadata namespaceType : namespaceTypes) {
            if (namespaceType instanceof ComplexTypeMetadata) {
                complexTypes.add((ComplexTypeMetadata) namespaceType);
            }
        }
        return complexTypes;
    }

    public Collection<TypeMetadata> getTypes() {
        List<TypeMetadata> allTypes = new LinkedList<TypeMetadata>();
        Collection<Map<String, TypeMetadata>> nameSpaces = entityTypes.values();
        for (Map<String, TypeMetadata> nameSpace : nameSpaces) {
            allTypes.addAll(nameSpace.values());
        }
        nameSpaces = nonInstantiableTypes.values();
        for (Map<String, TypeMetadata> nameSpace : nameSpaces) {
            allTypes.addAll(nameSpace.values());
        }
        return allTypes;
    }

    public TypeMetadata getNonInstantiableType(String namespace, String typeName) {
        if (namespace == null) {
            namespace = StringUtils.EMPTY;
        }
        Map<String, TypeMetadata> map = nonInstantiableTypes.get(namespace);
        if (map != null) {
            return map.get(typeName.trim());
        }
        return null;
    }

    public List<ComplexTypeMetadata> getNonInstantiableTypes() {
        Map<String, TypeMetadata> map = nonInstantiableTypes.get(USER_NAMESPACE);
        List<ComplexTypeMetadata> nonInstantiableTypes = new LinkedList<ComplexTypeMetadata>();
        if (map != null) {
            for (TypeMetadata typeMetadata : map.values()) {
                if (typeMetadata instanceof ComplexTypeMetadata) {
                    nonInstantiableTypes.add((ComplexTypeMetadata) typeMetadata);
                }
            }
        }
        return nonInstantiableTypes;
    }

    public void load(InputStream inputStream) {
        load(inputStream, new DefaultValidationHandler());
    }

    public void load(InputStream inputStream, ValidationHandler handler) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream can not be null.");
        }
        // Validates data model using shared studio / server classes
        // Load user defined data model now
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(Resource.OPTION_SAVE_ONLY_IF_CHANGED, Resource.OPTION_SAVE_ONLY_IF_CHANGED_MEMORY_BUFFER);
        XSDParser parse = new XSDParser(options);
        parse.parse(inputStream);
        XSDSchema schema = parse.getSchema();
        schema.validate();
        EList<XSDDiagnostic> diagnostics = schema.getDiagnostics();
        for (XSDDiagnostic diagnostic : diagnostics) {
            XSDDiagnosticSeverity severity = diagnostic.getSeverity();
            if (XSDDiagnosticSeverity.ERROR_LITERAL.equals(severity)) {
                handler.error((TypeMetadata) null, "XSD validation error: " + diagnostic.getMessage(), null, -1, -1, ValidationError.XML_SCHEMA);
            } else if (XSDDiagnosticSeverity.WARNING_LITERAL.equals(severity)) {
                handler.error((TypeMetadata) null, "XSD validation warning: " + diagnostic.getMessage(), null, -1, -1, ValidationError.XML_SCHEMA);
            }
        }
        XmlSchemaWalker.walk(schema, this);
        // TMDM-4876 Additional processing for entity inheritance
        resolveAdditionalSuperTypes(this);
        // "Freeze" all types (ensure all soft references now point to actual types in the repository).
        nonInstantiableTypes.put(getUserNamespace(), freezeTypes(nonInstantiableTypes.get(getUserNamespace())));
        // "Freeze" all reusable type usages in the data model.
        freezeUsages();
        entityTypes.put(getUserNamespace(), freezeTypes(entityTypes.get(getUserNamespace())));
        // Validate types
        for (TypeMetadata type : getUserComplexTypes()) {
            if (!XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(type.getNamespace())) {
                type.validate(handler);
            }
        }
        for (TypeMetadata type : getNonInstantiableTypes()) {
            if (!XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(type.getNamespace())) {
                type.validate(handler);
            }
        }
        handler.end();
        if (handler.getErrorCount() != 0) {
            LOGGER.error("Could not parse data model (" + handler.getErrorCount() + " error(s) found).");
        }
    }

    protected void freezeUsages() {
        for (List<ComplexTypeMetadata> entityTypes : entityTypeUsage.values()) {
            for (ComplexTypeMetadata entityType : entityTypes) {
                entityType.accept(new DefaultMetadataVisitor<Void>() {
                    @Override
                    public Void visit(ContainedComplexTypeMetadata containedType) {
                        containedType.getContainedType().declareUsage(containedType);
                        containedType.finalizeUsage();
                        for (ComplexTypeMetadata subType : containedType.getSubTypes()) {
                            subType.accept(this);
                        }
                        return super.visit(containedType);
                    }
                });
            }
        }
    }

    private Map<String, TypeMetadata> freezeTypes(Map<String, TypeMetadata> typesToFreeze) {
        if (typesToFreeze == null) {
            return null;
        }
        Map<String, TypeMetadata> workingTypes = new TreeMap<String, TypeMetadata>(typesToFreeze);
        for (TypeMetadata type : typesToFreeze.values()) {
            workingTypes.put(type.getName(), type.freeze());
        }
        return workingTypes;
    }

    private static void resolveAdditionalSuperTypes(MetadataRepository repository) {
        Collection<ComplexTypeMetadata> types = repository.getUserComplexTypes();
        for (TypeMetadata current : types) {
            String complexTypeName = current.getData(COMPLEX_TYPE_NAME);
            if (complexTypeName != null) {
                TypeMetadata nonInstantiableType = repository.getNonInstantiableType(USER_NAMESPACE, complexTypeName);
                if (!nonInstantiableType.getSuperTypes().isEmpty() && !nonInstantiableType.isFrozen()) {
                    TypeMetadata superType = nonInstantiableType.getSuperTypes().iterator().next();
                    ComplexTypeMetadata entitySuperType = null;
                    for (TypeMetadata entity : types) {
                        if (superType.getName().equals(entity.getData(COMPLEX_TYPE_NAME))) {
                            entitySuperType = (ComplexTypeMetadata) entity;
                            break;
                        }
                    }
                    if (entitySuperType != null) {
                        current.addSuperType(entitySuperType);
                    }
                }
            }
        }
    }

    public <T> T accept(MetadataVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public void addTypeMetadata(TypeMetadata typeMetadata) {
        String namespace = typeMetadata.getNamespace();
        if (typeMetadata.isInstantiable()) {
            registerType(typeMetadata, namespace, entityTypes);
        } else {
            registerType(typeMetadata, namespace, nonInstantiableTypes);
        }
    }

    private static void registerType(TypeMetadata typeMetadata, String namespace, Map<String, Map<String, TypeMetadata>> typeMap) {
        if (namespace == null) {
            namespace = StringUtils.EMPTY;
        }
        Map<String, TypeMetadata> nameSpace = typeMap.get(namespace);
        if (nameSpace == null) {
            nameSpace = new TreeMap<String, TypeMetadata>();
            typeMap.put(namespace, nameSpace);
        }
        typeMap.get(namespace).put(typeMetadata.getName(), typeMetadata);
    }


    public void close() {
        entityTypes.clear();
        nonInstantiableTypes.clear();
    }

    public Collection<TypeMetadata> getInstantiableTypes() {
        return entityTypes.get(USER_NAMESPACE).values();
    }

    @Override
    public void visitSchema(XSDSchema xmlSchema) {
        targetNamespace = xmlSchema.getTargetNamespace() == null ? USER_NAMESPACE : xmlSchema.getTargetNamespace();
        if (!currentTypeStack.isEmpty()) {
            if (LOGGER.isDebugEnabled()) {
                StringBuilder builder = new StringBuilder();
                for (ComplexTypeMetadata unprocessedType : currentTypeStack) {
                    builder.append(unprocessedType.getName()).append(" "); //$NON-NLS-1$
                }
                LOGGER.debug("Unprocessed types: " + builder);
            }
            // At the end of data model parsing, we expect all entity types to be processed.
            throw new IllegalStateException(currentTypeStack.size() + " types have not been correctly parsed.");
        }
    }

    @Override
    public void visitSimpleType(XSDSimpleTypeDefinition type) {
        String typeName = type.getName();
        TypeMetadata typeMetadata = getNonInstantiableType(targetNamespace, typeName);
        if (typeMetadata == null) {
            typeMetadata = new SimpleTypeMetadata(targetNamespace, typeName);
        }
        List<TypeMetadata> superTypes = new LinkedList<TypeMetadata>();
        if (typeName == null) {
            // Anonymous simple type (expects this is a restriction of a simple type or fails).
            XSDSimpleTypeDefinition baseTypeDefinition = type.getBaseTypeDefinition();
            if (baseTypeDefinition != null) {
                typeName = baseTypeDefinition.getName();
            } else {
                throw new NotImplementedException("Support for " + type);
            }
        } else {
            // Simple type might inherit from other simple types (i.e. UUID from string).
            XSDSimpleTypeDefinition baseType = type.getBaseTypeDefinition();
            if (baseType != null && baseType.getName() != null) {
                superTypes.add(new SoftTypeRef(this, baseType.getTargetNamespace(), baseType.getName(), false));
                EList<XSDConstrainingFacet> facets = type.getFacetContents();
                for (XSDConstrainingFacet currentFacet : facets) {
                    if (currentFacet instanceof XSDMaxLengthFacet) {
                        typeMetadata.setData(MetadataRepository.DATA_MAX_LENGTH, String.valueOf(((XSDMaxLengthFacet) currentFacet).getValue()));
                    } else if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Ignore simple type facet on type '" + typeName + "': " + currentFacet);
                    }
                }
            }
        }
        if (getNonInstantiableType(targetNamespace, typeName) == null) {
            for (TypeMetadata superType : superTypes) {
                typeMetadata.addSuperType(superType);
            }
            addTypeMetadata(typeMetadata);
        }
    }

    @Override
    public void visitComplexType(XSDComplexTypeDefinition type) {
        String typeName = type.getName();
        boolean isNonInstantiableType = currentTypeStack.isEmpty();
        if (isNonInstantiableType) {
            if (nonInstantiableTypes.get(getUserNamespace()) != null) {
                if (nonInstantiableTypes.get(getUserNamespace()).containsKey(typeName)) {
                    // Ignore another definition of type (already processed).
                    return;
                }
            }
            // There's no current 'entity' type being parsed, this is a complex type not to be used for entity but
            // might be referenced by others entities (for fields, inheritance...).
            ComplexTypeMetadata nonInstantiableType = new ComplexTypeMetadataImpl(targetNamespace, typeName, false);
            // Keep line and column of definition
            nonInstantiableType.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(type.getElement()));
            nonInstantiableType.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(type.getElement()));
            nonInstantiableType.setData(XSD_DOM_ELEMENT, type.getElement());
            addTypeMetadata(nonInstantiableType);
            currentTypeStack.push(nonInstantiableType);
            // If type is used, declare usage
            List<ComplexTypeMetadata> usages = entityTypeUsage.get(type);
            for (ComplexTypeMetadata usage : usages) {
                nonInstantiableType.declareUsage(usage);
            }
        } else {
            // Keep track of the complex type used for entity type (especially for inheritance).
            if (typeName != null) {
                currentTypeStack.peek().setData(MetadataRepository.COMPLEX_TYPE_NAME, typeName);
            }
        }
        XSDComplexTypeContent particle = type.getContent();
        if (particle instanceof XSDParticle) {
            XSDParticle currentParticle = (XSDParticle) particle;
            if (currentParticle.getTerm() instanceof XSDModelGroup) {
                XSDModelGroup group = (XSDModelGroup) currentParticle.getTerm();
                EList<XSDParticle> particles = group.getContents();
                for (XSDParticle p : particles) {
                    XSDParticleContent particleContent = p.getContent();
                    XmlSchemaWalker.walk(particleContent, this);
                }
            }
        } else if (particle != null) {
            throw new IllegalArgumentException("Not supported XML Schema particle: " + particle.getClass().getName());
        }
        // Adds the type information about super types.
        XSDTypeDefinition contentModel = type.getBaseTypeDefinition();
        if (contentModel != null) {
            if (!XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(contentModel.getTargetNamespace())
                    && !Types.ANY_TYPE.equals(contentModel.getName())) {
                SoftTypeRef superType = new SoftTypeRef(this,
                        contentModel.getTargetNamespace(),
                        contentModel.getName(),
                        false);
                if (currentTypeStack.peek() instanceof ContainedComplexTypeMetadata) {
                    superType.declareUsage(currentTypeStack.peek());
                }
                currentTypeStack.peek().addSuperType(superType);
                particle = type.getContent();
                if (particle instanceof XSDParticle) {
                    XSDParticle currentParticle = (XSDParticle) particle;
                    if (currentParticle.getTerm() instanceof XSDModelGroup) {
                        XSDModelGroup group = (XSDModelGroup) currentParticle.getTerm();
                        EList<XSDParticle> particles = group.getContents();
                        for (XSDParticle p : particles) {
                            XSDParticleContent particleContent = p.getContent();
                            XmlSchemaWalker.walk(particleContent, this);
                        }
                    }
                } else if (particle != null) {
                    throw new IllegalArgumentException("Not supported XML Schema particle: " + particle.getClass().getName());
                }
            }
        }
        if (isNonInstantiableType) {
            currentTypeStack.pop();
        }
    }

    @Override
    public void visitElement(XSDElementDeclaration element) {
        if (currentTypeStack.isEmpty()) { // "top level" elements means new MDM entity type
            String typeName = element.getName();
            if (getComplexType(typeName) != null) { // Don't process twice type
                return;
            }
            // Id fields
            Map<String, XSDXPathDefinition> idFields = new HashMap<String, XSDXPathDefinition>();
            EList<XSDIdentityConstraintDefinition> constraints = element.getIdentityConstraintDefinitions();
            for (XSDIdentityConstraintDefinition constraint : constraints) {
                EList<XSDXPathDefinition> fields = constraint.getFields();
                for (XSDXPathDefinition field : fields) {
                    idFields.put(field.getValue(), field);
                }
            }
            XmlSchemaAnnotationProcessorState state;
            try {
                XSDAnnotation annotation = element.getAnnotation();
                state = new XmlSchemaAnnotationProcessorState();
                for (XmlSchemaAnnotationProcessor processor : XML_ANNOTATIONS_PROCESSORS) {
                    processor.process(this, null, annotation, state);
                }
            } catch (Exception e) {
                throw new RuntimeException("Annotation processing exception while parsing info for type '" + typeName + "'.",
                        e);
            }
            // If write is not allowed for everyone, at least add "administration".
            if (state.getAllowWrite().isEmpty()) {
                state.getAllowWrite().add(ICoreConstants.ADMIN_PERMISSION);
            }
            ComplexTypeMetadata type = new ComplexTypeMetadataImpl(targetNamespace, typeName, state.getAllowWrite(), state.getDenyCreate(),
                    state.getHide(), state.getDenyPhysicalDelete(), state.getDenyLogicalDelete(), state.getSchematron(),
                    state.getPrimaryKeyInfo(), state.getLookupFields(), true, state.getWorkflowAccessRights());
            // Keep line and column of definition
            type.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(element.getElement()));
            type.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(element.getElement()));
            type.setData(XSD_DOM_ELEMENT, element.getElement());
            addTypeMetadata(type);
            // Keep usage information
            entityTypeUsage.get(element.getType()).add(type);
            // Walk the fields
            currentTypeStack.push(type);
            {
                XmlSchemaWalker.walk(element.getType(), this);
            }
            currentTypeStack.pop();
            // Super types
            XSDElementDeclaration substitutionGroup = element.getSubstitutionGroupAffiliation();
            if (substitutionGroup != null
                    && !XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(substitutionGroup.getTargetNamespace())
                    && !Types.ANY_TYPE.equals(substitutionGroup.getName())) {
                if (!substitutionGroup.getResolvedElementDeclaration().equals(element)) {
                    SoftTypeRef superType = new SoftTypeRef(this,
                            substitutionGroup.getTargetNamespace(),
                            substitutionGroup.getName(),
                            true);
                    type.addSuperType(superType);
                }
            }
            // Register keys (TMDM-4470).
            for (Map.Entry<String, XSDXPathDefinition> unresolvedId : idFields.entrySet()) {
                SoftIdFieldRef keyField = new SoftIdFieldRef(this, type.getName(), unresolvedId.getKey());
                // Keep line and column of definition
                keyField.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(unresolvedId.getValue().getElement()));
                keyField.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(unresolvedId.getValue().getElement()));
                keyField.setData(XSD_DOM_ELEMENT, unresolvedId.getValue().getElement());
                type.registerKey(keyField);
            }
            // TMDM-6264: An entity type without any key info is a element maybe referenced by others, but never an
            // entity.
            if (type.getKeyFields().isEmpty() && type.getSuperTypes().isEmpty()) {
                Map<String, TypeMetadata> userEntityTypes = entityTypes.get(getUserNamespace());
                if (userEntityTypes != null) {
                    userEntityTypes.remove(type.getName());
                }
            }
        } else { // Non "top level" elements means fields for the MDM entity type being parsed
            FieldMetadata fieldMetadata;
            int minOccurs = ((XSDParticle) element.getContainer()).getMinOccurs();
            int maxOccurs = ((XSDParticle) element.getContainer()).getMaxOccurs();
            if (element.getResolvedElementDeclaration() != null
                    && element.getResolvedElementDeclaration().getTargetNamespace() == null) {
                fieldMetadata = createFieldMetadata(element.getResolvedElementDeclaration(),
                        currentTypeStack.peek(),
                        minOccurs,
                        maxOccurs);
            } else {
                fieldMetadata = createFieldMetadata(element,
                        currentTypeStack.peek(),
                        minOccurs,
                        maxOccurs);
            }
            currentTypeStack.peek().addField(fieldMetadata);
        }
    }

    // TODO Refactor!
    private FieldMetadata createFieldMetadata(XSDElementDeclaration element, ComplexTypeMetadata containingType, int minOccurs,
            int maxOccurs) {
        String fieldName = element.getName();
        if (maxOccurs > 0 && minOccurs > maxOccurs) { // Eclipse XSD does not check this
            throw new IllegalArgumentException("Can not parse information on field '"
                    + element.getQName()
                    + "' of type '"
                    + containingType + "' (maxOccurs > minOccurs)");
        }
        boolean isMany = maxOccurs == -1 || maxOccurs > 1;
        XmlSchemaAnnotationProcessorState state = new XmlSchemaAnnotationProcessorState();
        try {
            XSDAnnotation annotation = element.getAnnotation();
            for (XmlSchemaAnnotationProcessor processor : XML_ANNOTATIONS_PROCESSORS) {
                processor.process(this, containingType, annotation, state);
            }
        } catch (Exception e) {
            throw new RuntimeException("Annotation processing exception while parsing info for field '" + fieldName + "' in type '" + containingType.getName() + "'", e);
        }
        boolean isMandatory = minOccurs > 0;
        boolean isContained = false;
        boolean isReference = state.isReference();
        boolean fkIntegrity = state.isFkIntegrity();
        boolean fkIntegrityOverride = state.isFkIntegrityOverride();
        List<FieldMetadata> foreignKeyInfo = state.getForeignKeyInfo();
        TypeMetadata fieldType = state.getFieldType();
        FieldMetadata referencedField = state.getReferencedField();
        TypeMetadata referencedType = state.getReferencedType();
        List<String> hideUsers = state.getHide();
        List<String> allowWriteUsers = state.getAllowWrite();
        List<String> workflowAccessRights = state.getWorkflowAccessRights();
        XSDTypeDefinition schemaType = element.getType();
        if (schemaType instanceof XSDSimpleTypeDefinition) {
            XSDSimpleTypeDefinition simpleSchemaType = (XSDSimpleTypeDefinition) schemaType;
            XSDSimpleTypeDefinition content = simpleSchemaType.getBaseTypeDefinition();
            if (schemaType.getQName() != null) { // Null QNames may happen for anonymous types extending other types.
                fieldType = new SoftTypeRef(this, schemaType.getTargetNamespace(), schemaType.getName(), false);
                fieldType.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(element.getElement()));
                fieldType.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(element.getElement()));
                fieldType.setData(XSD_DOM_ELEMENT, element.getElement());
            }
            if (isReference) {
                ReferenceFieldMetadata referenceField = new ReferenceFieldMetadata(containingType,
                        false,
                        isMany,
                        isMandatory,
                        fieldName,
                        (ComplexTypeMetadata) referencedType,
                        referencedField,
                        foreignKeyInfo,
                        fkIntegrity,
                        fkIntegrityOverride,
                        fieldType,
                        allowWriteUsers,
                        hideUsers,
                        workflowAccessRights);
                referenceField.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(element.getElement()));
                referenceField.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(element.getElement()));
                referenceField.setData(XSD_DOM_ELEMENT, element.getElement());
                return referenceField;
            }
            if (content != null) {
                if (fieldType == null) {
                    fieldType = new SimpleTypeMetadata(content.getTargetNamespace(), content.getName());
                    EList<XSDConstrainingFacet> facets = simpleSchemaType.getFacetContents();
                    for (XSDConstrainingFacet currentFacet : facets) {
                        if (currentFacet instanceof XSDMaxLengthFacet) {
                            fieldType.setData(MetadataRepository.DATA_MAX_LENGTH, String.valueOf(((XSDMaxLengthFacet) currentFacet).getValue()));
                        } else if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Ignore simple type facet on type '" + fieldName + "': " + currentFacet);
                        }
                    }
                }
                if (content.getFacets().size() > 0) {
                    boolean isEnumeration = false;
                    for (int i = 0; i < content.getFacets().size(); i++) {
                        XSDConstrainingFacet item = content.getFacets().get(i);
                        if (item instanceof XSDEnumerationFacet) {
                            isEnumeration = true;
                        }
                    }
                    if (isEnumeration) {
                        EnumerationFieldMetadata enumField = new EnumerationFieldMetadata(containingType,
                                false,
                                isMany,
                                isMandatory,
                                fieldName,
                                fieldType,
                                allowWriteUsers,
                                hideUsers,
                                workflowAccessRights);
                        enumField.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(element.getElement()));
                        enumField.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(element.getElement()));
                        enumField.setData(XSD_DOM_ELEMENT, element.getElement());
                        return enumField;
                    } else {
                        FieldMetadata field = new SimpleTypeFieldMetadata(containingType,
                                false,
                                isMany,
                                isMandatory,
                                fieldName,
                                fieldType,
                                allowWriteUsers,
                                hideUsers,
                                workflowAccessRights);
                        field.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(element.getElement()));
                        field.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(element.getElement()));
                        field.setData(XSD_DOM_ELEMENT, element.getElement());
                        return field;
                    }
                } else {
                    FieldMetadata field = new SimpleTypeFieldMetadata(containingType,
                            false,
                            isMany,
                            isMandatory,
                            fieldName,
                            fieldType,
                            allowWriteUsers,
                            hideUsers,
                            workflowAccessRights);
                    field.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(element.getElement()));
                    field.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(element.getElement()));
                    field.setData(XSD_DOM_ELEMENT, element.getElement());
                    return field;
                }
            }
        }
        if (fieldType == null) {
            String qName = element.getType() == null ? null : element.getType().getQName();
            if (qName != null) {
                TypeMetadata metadata = getType(element.getType().getTargetNamespace(), element.getType().getName());
                if (metadata != null) {
                    fieldType = new SoftTypeRef(this, targetNamespace, schemaType.getName(), false);
                    isContained = true;
                } else {
                    if (schemaType instanceof XSDComplexTypeDefinition) {
                        fieldType = new SoftTypeRef(this, schemaType.getTargetNamespace(), schemaType.getName(), false);
                        isContained = true;
                    } else {
                        throw new NotImplementedException("Support for '" + schemaType.getClass() + "'.");
                    }
                }
            } else { // Ref & anonymous complex type
                isContained = true;
                XSDElementDeclaration refName = element.getResolvedElementDeclaration();
                if (schemaType != null) {
                    fieldType = new ComplexTypeMetadataImpl(targetNamespace, ANONYMOUS_PREFIX + String.valueOf(anonymousCounter++), false);
                    isContained = true;
                } else if (refName != null) {
                    // Reference being an element, consider references as references to entity type.
                    fieldType = new SoftTypeRef(this, refName.getTargetNamespace(), refName.getName(), true);
                } else {
                    throw new NotImplementedException();
                }
            }
        }
        if (isContained) {
            ContainedTypeFieldMetadata containedField = new ContainedTypeFieldMetadata(containingType,
                    isMany,
                    isMandatory,
                    fieldName,
                    (ComplexTypeMetadata) fieldType,
                    allowWriteUsers,
                    hideUsers,
                    workflowAccessRights);
            containedField.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(element.getElement()));
            containedField.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(element.getElement()));
            containedField.setData(XSD_DOM_ELEMENT, element.getElement());
            if (fieldType.getName().startsWith(ANONYMOUS_PREFIX)) {
                currentTypeStack.push((ComplexTypeMetadata) containedField.getType());
                {
                    XmlSchemaWalker.walk(schemaType, this);
                }
                currentTypeStack.pop();
            }
            return containedField;
        } else {
            FieldMetadata field = new SimpleTypeFieldMetadata(containingType,
                    false,
                    isMany,
                    isMandatory,
                    fieldName,
                    fieldType,
                    allowWriteUsers,
                    hideUsers,
                    workflowAccessRights);
            field.setData(XSD_LINE_NUMBER, XSDParser.getStartLine(element.getElement()));
            field.setData(XSD_COLUMN_NUMBER, XSDParser.getStartColumn(element.getElement()));
            field.setData(XSD_DOM_ELEMENT, element.getElement());
            return field;
        }
    }

    private static class NoOpValidationHandler implements ValidationHandler {
        @Override
        public void error(TypeMetadata type, String message, Element element, Integer lineNumber, Integer columnNumber, ValidationError error) {
            // Nothing to do (No op validation)
        }

        @Override
        public void fatal(FieldMetadata field, String message, Element element, Integer lineNumber, Integer columnNumber, ValidationError error) {
            // Nothing to do (No op validation)
        }

        @Override
        public void error(FieldMetadata field, String message, Element element, Integer lineNumber, Integer columnNumber, ValidationError error) {
            // Nothing to do (No op validation)
        }

        @Override
        public void warning(FieldMetadata field, String message, Element element, Integer lineNumber, Integer columnNumber, ValidationError error) {
            // Nothing to do (No op validation)
        }

        @Override
        public void fatal(TypeMetadata type, String message, Element element, Integer lineNumber, Integer columnNumber, ValidationError error) {
            // Nothing to do (No op validation)
        }

        @Override
        public void warning(TypeMetadata type, String message, Element element, Integer lineNumber, Integer columnNumber, ValidationError error) {
            // Nothing to do (No op validation)
        }

        @Override
        public void end() {
            // Nothing to do (No op validation)
        }

        @Override
        public int getErrorCount() {
            return 0;
        }
    }
}
