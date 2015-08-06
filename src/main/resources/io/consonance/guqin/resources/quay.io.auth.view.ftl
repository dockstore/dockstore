<#-- @ftlvariable name="" type="io.consonance.guqin.resources.QuayIOAuthenticationResource.QuayIOView" -->
<html>
    <body>
        <p>Client ID: ${parent.clientID?html}</p>
        <p>redirectURI: ${parent.redirectURI?html}</p>

         <a href="https://quay.io/oauth/authorize?response_type=token&redirect_uri=${parent.redirectURI?html}&realm=realm&client_id=${parent.clientID?html}&scope=repo:read">Authorization link</a> 
    </body>
</html>
