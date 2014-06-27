/*
<<<<<<< HEAD:org.talend.mdm.commmon/src/org/talend/mdm/commmon/metadata/annotation/ForeignKeyProcessor.java
 * Copyright (C) 2006-2013 Talend Inc. - www.talend.com
 *
=======
 * Copyright (C) 2006-2014 Talend Inc. - www.talend.com
 * 
>>>>>>> e100b9c... TMDM-7482: "view contains complex type fields throw error in webUI":main/plugins/org.talend.mdm.commmon/src/org/talend/mdm/commmon/metadata/annotation/ForeignKeyProcessor.java
 * This source code is available under agreement available at
 * %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
 * 
 * You should have received a copy of the agreement along with this program; if not, write to Talend SA 9 rue Pages
 * 92150 Suresnes, France
 */

package org.talend.mdm.commmon.metadata.annotation;

import org.apache.commons.lang.StringUtils;
import org.eclipse.emf.common.util.EList;
import org.eclipse.xsd.XSDAnnotation;
import org.eclipse.xsd.util.XSDParser;
import org.talend.mdm.commmon.metadata.*;
import org.w3c.dom.Element;

public class ForeignKeyProcessor implements XmlSchemaAnnotationProcessor {

    @Override
    public void process(MetadataRepository repository, ComplexTypeMetadata type, XSDAnnotation annotation,
            XmlSchemaAnnotationProcessorState state) {
        if (annotation != null) {
            EList<Element> annotations = annotation.getApplicationInformation();
            for (Element appInfo : annotations) {
                String source = appInfo.getAttribute("source");
                if ("X_ForeignKey".equals(source)) { //$NON-NLS-1$
                    handleForeignKey(repository, type, state, appInfo);
                } else if ("X_ForeignKeyInfo".equals(source)) { //$NON-NLS-1$
                    handleForeignKeyInfo(repository, type, state, appInfo);
                } else if ("X_FKIntegrity".equals(source)) { //$NON-NLS-1$
                    state.setFkIntegrity(Boolean.valueOf(appInfo.getTextContent()));
                } else if ("X_FKIntegrity_Override".equals(source)) { //$NON-NLS-1$
                    state.setFkIntegrityOverride(Boolean.valueOf(appInfo.getTextContent()));
                }
            }
        }
    }

    private void handleForeignKeyInfo(MetadataRepository repository, ComplexTypeMetadata type,
            XmlSchemaAnnotationProcessorState state, Element appInfo) {
        String path = appInfo.getTextContent();
        FieldMetadata fieldMetadata = getFieldMetadata(repository, type, state, appInfo, path);
        state.setForeignKeyInfo(fieldMetadata);
    }

    private void handleForeignKey(MetadataRepository repository, ComplexTypeMetadata type,
            XmlSchemaAnnotationProcessorState state, Element appInfo) {
        state.markAsReference();
        String path = appInfo.getTextContent();
        FieldMetadata fieldMetadata = getFieldMetadata(repository, type, state, appInfo, path);
        state.setReferencedField(fieldMetadata);
    }

    private static FieldMetadata getFieldMetadata(MetadataRepository repository, ComplexTypeMetadata type,
            XmlSchemaAnnotationProcessorState state, Element appInfo, String path) {
        String typeName = StringUtils.substringBefore(path, "/").trim(); //$NON-NLS-1$
        if (typeName.equals(".")) { //$NON-NLS-1$
            typeName = type.getName();
        }
        String fieldPath = StringUtils.substringAfter(path, "/").trim(); //$NON-NLS-1$
        String userNamespace = repository.getUserNamespace();
        SoftTypeRef referencedType = new SoftTypeRef(repository, userNamespace, typeName, true);
        state.setFieldType(referencedType); // TODO Wrong!!!!
        state.setReferencedType(referencedType); // Only reference instantiable types.
        FieldMetadata fieldMetadata;
        if (!fieldPath.isEmpty()) {
            fieldMetadata = new SoftFieldRef(repository, fieldPath, typeName);
        } else {
            fieldMetadata = new SoftIdFieldRef(repository, typeName);
        }
        fieldMetadata.setData(MetadataRepository.XSD_LINE_NUMBER, XSDParser.getStartLine(appInfo));
        fieldMetadata.setData(MetadataRepository.XSD_COLUMN_NUMBER, XSDParser.getStartColumn(appInfo));
        fieldMetadata.setData(MetadataRepository.XSD_DOM_ELEMENT, appInfo);
        return fieldMetadata;
    }

}
