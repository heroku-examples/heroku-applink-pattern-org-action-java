Heroku Integration - Extending Apex, Flow and Agentforce - Java
===============================================================

> [!IMPORTANT]
> For use with the Heroku Integration and Heroku Eventing pilots only

This sample demonstrates importing a Heroku application into an org to enable Apex, Flow, and Agentforce to call out to Heroku. For Apex, both synchronous and asynchronous invocation are demonstrated, along with securely elevating Salesforce permissions for processing that requires additional object or field access.

The scenario used in this sample illustrates performing calculations over Salesforce **Opportunity** data and storing the result back in Salesforce as a **Quote**. Calculating Quote information from Opportunities can become quite intensive, especially when large multinational businesses have complex rules that impact pricing related to region, products, and discount thresholds. It's also possible that such code already exists, and there is a desire to reuse it within a Salesforce context.

Requirements
------------
- Heroku login
- Heroku Integration Pilot enabled
- Heroku CLI installed
- Salesforce CLI installed
- Login information for one or more Development or Sandbox orgs

## Local Development and Testing

Code invoked from Salesforce requires specific HTTP headers to connect back to the invoking Salesforce org. Using the `invoke.sh` script supplied with this sample, it is possible to simulate requests from Salesforce with the correct headers, enabling you to develop and test locally before deploying to test from Apex, Flow, and Agentforce. This sample leverages the `sf` CLI to allow the `invoke.sh` script to access org authentication details. Run the following commands to locally authenticate, build and run the sample:

```
sf org login web --alias my-org
mvn clean install
mvn spring-boot:run
```

In a new terminal window run the following command substituing `006am000006pS6P` for a valid **Opportunity Id** record from your Salesforce org, ensuring you identify an **Opportunity** that also has related **Product** line items.

```
./bin/invoke.sh my-org 006am000006pS6P
```

You should see the following output:

```
Response from server:
{"quoteId":"0Q0am000000nRLdCAM"}
```

You can now also view the **Quote** by refreshing the **Opportunity** page within Salesforce.

## Deploying and Testing from Apex and Flow

To test from Apex, Flow and other tools within your Salesforce org you must deploy the code and import it into your org. The following commands create a Heroku application and configure the Heroku Integration add-on. This add-on and associated buildpack allows secure authenticated access from within your code and visibility of your code from Apex, Flow and Agentforce. After this configuration, code is not accessible from the public internet, only from within an authorized Salesforce org.

```
heroku create
heroku addons:create heroku-integration
heroku buildpacks:add https://github.com/heroku/heroku-buildpack-heroku-integration-service-mesh
heroku salesforce:connect my-org --store-as-run-as-user
heroku salesforce:import api-docs.yaml --org-name my-org --client-name GenerateQuote
```

Once imported grant permisisons to users to invoke your code using the following `sf` command:

```
sf org assign permset --name GenerateQuote -o my-org
```

Deploy the Heroku application and confirm it has started.

```
git push heroku main
heroku logs
```

Navigate to your orgs **Setup** menu and search for **Heroku** then click **Apps** to confirm your application has been imported.

### Invoking from Apex

The following Apex code is run using the `sf` command line for ease, however you can add this code existing or new Apex code. Make sure to change the **Opportunity Id** `006am000006pS6P` below to a valid **Opportunity** from your org (see above).

```
echo \
"ExternalService.GenerateQuote service = new ExternalService.GenerateQuote();" \
"ExternalService.GenerateQuote.generateQuote_Request request = new ExternalService.GenerateQuote.generateQuote_Request();" \
"ExternalService.GenerateQuote_QuoteGenerationRequest body = new ExternalService.GenerateQuote_QuoteGenerationRequest();" \
"body.opportunityId = '006am000006pS6P';" \
"request.body = body;" \
"System.debug(service.generateQuote(request).Code200.quoteId);" \
| sf apex run -o my-org
```

### Invoking from Flow


## Permissions and Permission Elevation

Authenticated connections passed to your code are created from the user identity within Salesforce that causes directly or indirectly a given Apex, Flow or Agentforce operation to invoke your code. As such, in contrast with Apex, your code always runs in the more secure User mode, not System mode. If your code needs to access or manipulate informatoin not visible to the user, you need to use the approach described above to elevate permissions of your code to access fields and objects not visible to the user.

## Invoking from Agentforce

bla bla 

## Technical Information
- 

## Other Samples

| Sample | What it covers? |
| ------ | --------------- |
| [Salesforce API Access - Java](https://github.com/heroku-examples/heroku-integration-pattern-api-access-java) | This sample application showcases how to extend a Heroku web application by integrating it with Salesforce APIs, enabling seamless data exchange and automation across multiple connected Salesforce orgs. It also includes a demonstration of the Salesforce Bulk API, which is optimized for handling large data volumes efficiently. |
| [Extending Apex, Flow and Agentforce - Java](https://github.com/heroku-examples/heroku-integration-pattern-org-action-java) | This sample demonstrates importing a Heroku application into an org to enable Apex, Flow, and Agentforce to call out to Heroku. For Apex, both synchronous and asynchronous invocation are demonstrated, along with securely elevating Salesforce permissions for processing that requires additional object or field access. |
| [Scaling Batch Jobs with Heroku - Java](https://github.com/heroku-examples/heroku-integration-pattern-org-job-java) | This sample seamlessly delegates the processing of large amounts of data with significant compute requirements to Heroku Worker processes. It also demonstrates the use of the Unit of Work aspect of the SDK (JavaScript only for the pilot) for easier utilization of the Salesforce Composite APIs. |
| [Using Eventing to drive Automation and Communication](https://github.com/heroku-examples/heroku-integration-pattern-eventing-java) | This sample extends the batch job sample by adding the ability to use eventing to start the work and notify users once it completes using Custom Notifications. These notifications are sent to the user's desktop or mobile device running Salesforce Mobile. Flow is used in this sample to demonstrate how processing can be handed off to low-code tools such as Flow. |
