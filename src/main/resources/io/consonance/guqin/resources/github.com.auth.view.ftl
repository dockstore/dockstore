<#-- @ftlvariable name="" type="io.consonance.guqin.resources.GitHubComAuthenticationResource.GithubComView" -->
<html>
    <body>
        <p>Client ID: ${parent.clientID?html}</p>
        <p>redirectURI: ${parent.redirectURI?html}</p>

         <a href="https://github.com/login/oauth/authorize?redirect_uri=${parent.redirectURI?html}&client_id=${parent.clientID?html}&scope=repo:read">Authorization link</a> 
    </body>
</html>
