// This class was generated by the JAXRPC SI, do not edit.
// Contents subject to change without notice.
// JAX-RPC Standard Implementation ��1.1.2_01������� R40��
// Generated source version: 1.1.2

package com.amalto.workbench.webservices;


public class WSDeleteBusinessConcept {
    protected com.amalto.workbench.webservices.WSDataModelPK wsDataModelPK;
    protected java.lang.String businessConceptName;
    
    public WSDeleteBusinessConcept() {
    }
    
    public WSDeleteBusinessConcept(com.amalto.workbench.webservices.WSDataModelPK wsDataModelPK, java.lang.String businessConceptName) {
        this.wsDataModelPK = wsDataModelPK;
        this.businessConceptName = businessConceptName;
    }
    
    public com.amalto.workbench.webservices.WSDataModelPK getWsDataModelPK() {
        return wsDataModelPK;
    }
    
    public void setWsDataModelPK(com.amalto.workbench.webservices.WSDataModelPK wsDataModelPK) {
        this.wsDataModelPK = wsDataModelPK;
    }
    
    public java.lang.String getBusinessConceptName() {
        return businessConceptName;
    }
    
    public void setBusinessConceptName(java.lang.String businessConceptName) {
        this.businessConceptName = businessConceptName;
    }
}
