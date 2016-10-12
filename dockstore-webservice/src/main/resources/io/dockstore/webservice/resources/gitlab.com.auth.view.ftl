<#-- @ftlvariable name="" type="io.consonance.webservice.resources.GitLabComAuthenticationResource.GitlabComView" -->
<html>
    <body>
        <p>Client ID: ${parent.clientID?html}</p>
        <p>redirectURI: ${parent.redirectURI?html}</p>

         <a href="https://gitlab.com/oauth/authorize?redirect_uri=${parent.redirectURI?html}&client_id=${parent.clientID?html}&response_type=code">Authorization link</a>
    </body>
</html>
