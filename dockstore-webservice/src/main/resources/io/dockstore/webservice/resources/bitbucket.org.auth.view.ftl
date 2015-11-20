<#-- @ftlvariable name="" type="io.dockstore.webservice.resources.BitbucketOrgAuthenticationResource.BitbucketOrgView" -->
<html>
    <body>
        <p>Client ID: ${parent.clientID?html}</p>
        

         <a href="https://bitbucket.org/site/oauth2/authorize?client_id=${parent.clientID?html}&response_type=code">Authorization link</a> 
    </body>
</html>
