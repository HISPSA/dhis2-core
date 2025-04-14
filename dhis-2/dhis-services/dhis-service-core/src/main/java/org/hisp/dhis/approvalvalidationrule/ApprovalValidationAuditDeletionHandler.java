package org.hisp.dhis.approvalvalidationrule;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hisp.dhis.category.CategoryOptionCombo;
import org.hisp.dhis.dataset.DataSet;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.system.deletion.DeletionHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component( "org.hisp.dhis.approvalvalidationrule.ApprovalValidationAuditDeletionHandler" )
public class ApprovalValidationAuditDeletionHandler
    extends DeletionHandler
{
    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

	protected static final String ERROR = "";
	
	private final JdbcTemplate jdbcTemplate;

    public ApprovalValidationAuditDeletionHandler(JdbcTemplate jdbcTemplate) {
    	checkNotNull( jdbcTemplate );
    	
		this.jdbcTemplate = jdbcTemplate;
	}

	// -------------------------------------------------------------------------
    // DeletionHandler implementation
    // -------------------------------------------------------------------------


    public String getClassName()
    {
        return ApprovalValidationAudit.class.getSimpleName();
    }
    

    public String allowDeleteDataSet( DataSet dataSet )
    {
        String sql = "SELECT COUNT(*) FROM approvalValidationaudit where dataelementid=" + dataSet.getId();
        
        return jdbcTemplate.queryForObject( sql, Integer.class ) == 0 ? null : ERROR;
    }
    

    public String allowDeleteApprovalValidationRule( ApprovalValidationRule approvalValidationRule )
    {
        String sql = "SELECT COUNT(*) FROM approvalValidationaudit where approvalvalidationruleid=" + approvalValidationRule.getId();
        
        return jdbcTemplate.queryForObject( sql, Integer.class ) == 0 ? null : ERROR;
    }
    

    public String allowDeletePeriod( Period period )
    {
        String sql = "SELECT COUNT(*) FROM approvalValidationaudit where periodid=" + period.getId();
        
        return jdbcTemplate.queryForObject( sql, Integer.class ) == 0 ? null : ERROR;
    }
    

    public String allowDeleteOrganisationUnit( OrganisationUnit unit )
    {
        String sql = "SELECT COUNT(*) FROM approvalValidationaudit where organisationunitid=" + unit.getId();
        
        return jdbcTemplate.queryForObject( sql, Integer.class ) == 0 ? null : ERROR;
    }
    

    public String allowDeleteCategoryOptionCombo( CategoryOptionCombo optionCombo )
    {
        String sql = "SELECT COUNT(*) FROM datavalueaudit where attributeoptioncomboid=" + optionCombo.getId();
        
        return jdbcTemplate.queryForObject( sql, Integer.class ) == 0 ? null : ERROR;
    }

	@Override
	protected void register() {
		whenDeleting(DataSet.class, this::allowDeleteDataSet);
		whenDeleting(ApprovalValidationRule.class, this::allowDeleteApprovalValidationRule);
		whenDeleting(Period.class, this::allowDeletePeriod);
		whenDeleting(OrganisationUnit.class, this::allowDeleteOrganisationUnit);
		whenDeleting(CategoryOptionCombo.class, this::allowDeleteCategoryOptionCombo);

	}
}
