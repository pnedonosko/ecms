/**
 * Editor buttons module.
 */
(function($, cCometD) {
  "use strict";

  /** For debug logging. */
  var log = function(msg, err) {
    var logPrefix = "[editorbuttons] ";
    if (typeof console != "undefined" && typeof console.log != "undefined") {
      var isoTime = " -- " + new Date().toISOString();
      var msgLine = msg;
      if (err) {
        msgLine += ". Error: ";
        if (err.name || err.message) {
          if (err.name) {
            msgLine += "[" + err.name + "] ";
          }
          if (err.message) {
            msgLine += err.message;
          }
        } else {
          msgLine += (typeof err === "string" ? err : JSON.stringify(err)
              + (err.toString && typeof err.toString === "function" ? "; " + err.toString() : ""));
        }

        console.log(logPrefix + msgLine + isoTime);
        if (typeof err.stack != "undefined") {
          console.log(err.stack);
        }
      } else {
        if (err !== null && typeof err !== "undefined") {
          msgLine += ". Error: '" + err + "'";
        }
        console.log(logPrefix + msgLine + isoTime);
      }
    }
  };
  
  var pageBaseUrl = function(theLocation) {
    if (!theLocation) {
      theLocation = window.location;
    }

    var theHostName = theLocation.hostname;
    var theQueryString = theLocation.search;

    if (theLocation.port) {
      theHostName += ":" + theLocation.port;
    }

    return theLocation.protocol + "//" + theHostName;
  };
  
  
  
  /**
   * Editor core class.
   */
  function EditorButtons() {

    // Functions to create editor buttons
    var buttonsFns = []; 
    var prefixUrl = pageBaseUrl(location);
    var currentWorkspace;
    var currentUserId;
    var subscribedDocuments = {};

    // CometD transport bus
    var cometd, cometdContext;
    
    const DOCUMENT_OPENED = "DOCUMENT_OPENED";
    const DOCUMENT_CLOSED = "DOCUMENT_CLOSED";
    const LAST_EDITOR_CLOSED = "LAST_EDITOR_CLOSED";
    const DOCUMENT_PREVIEW_OPENED = "DOCUMENT_PREVIEW_OPENED";
    const CURRENT_PROVIDER_INFO = "CURRENT_PROVIDER_INFO";
    
    /**
     * Parses comet message from JSON
     */
    var tryParseJson = function(message) {
      var src = message.data ? message.data : (message.error ? message.error : message.failure);
      if (src) {
        try {
          if (typeof src === "string" && (src.startsWith("{") || src.startsWith("["))) {
            return JSON.parse(src);
          }
        } catch (e) {
          log("Error parsing '" + src + "' as JSON: " + e, e);
        }
      }
      return src;
    };
    
    /**
     * Saves prefered provider.
     * 
     */
    var savePrefferedProvider = function(fileId, provider){
      $.post({
        async : true,
        type : "POST",
        url : prefixUrl + "/portal/rest/documents/editors/prefered/" + fileId,
        data : {
          userId : eXo.env.portal.userName,
          provider : provider,
          workspace : currentWorkspace
        }
      }).then(function(result) {
        log("Prefered provider " + provider + " saved. " + result);
      }).catch(function(xhr,status,error) {
        log("Cannot save prefered provider " + provider + ": " + status + " " + error);
      });
    };
    
    /**
     * Adds editor buttons container (button and pulldown)
     */
    var addEditorButtonsContainer = function($target, fileId, buttons) {
      if(!buttons) {
        return;
      }
      // Add buttons container
      var $container = $target.find(".editorButtonContainer");
      if ($container.length == 0) {
        $container = $("<div class='editorButtonContainer hidden-tabletL'></div>");
        $target.append($container);
      }
      
      // Create editor button
      var $btn = buttons[0].createButtonFn();
      $btn.addClass("editorButton");
      $btn.attr('data-provider', buttons[0].provider);
      $btn.attr('data-fileId', fileId);
      $container.append($btn);
      let provider = buttons[0].provider;
      $btn.click(function() {
        log("prefered provider: " + provider);
        savePrefferedProvider(fileId, provider);
      });
      
      // Create pulldown with editor buttons
      if(buttons.length > 1) {
        var $dropdownContainer = $("<div class='dropdown-container'></div>");
        var $toggle = $("<button class='btn dropdown-toggle' data-toggle='dropdown'>" +
        "<i class='uiIconArrowDown uiIconLightGray'></i></span></button>");
        
        var $dropdown = $("<ul class='dropdown-menu'></ul>");
        
        for(var i = 1; i < buttons.length; i++) {
          var $btn = buttons[i].createButtonFn();
          let provider = buttons[i].provider;
          // Save user choice
          $btn.click(function() {
            savePrefferedProvider(fileId, provider);
          });
          $btn.addClass("editorButton");
          $btn.attr('data-provider', buttons[i].provider);
          $btn.attr('data-fileId', fileId);
          $dropdown.append($btn);
        }
        $dropdownContainer.append($toggle);
        $dropdownContainer.append($dropdown);
        $container.append($dropdownContainer);
      }
    };
    
    /**
     * Adds the 'Edit Online' button to a preview (from the activity stream) when it's loaded.
     */
    var tryAddEditorButtonToPreview = function(attempts, delay, fileId, workspace, buttons) {
      var $elem = $("#uiDocumentPreview .previewBtn");
      if ($elem.length == 0 || !$elem.is(":visible")) {
        if (attempts > 0) {
          setTimeout(function() {
            tryAddEditorButtonToPreview(attempts - 1, delay, fileId, workspace, buttons);
          }, delay);
        } else {
          log("Cannot find element " + $elem);
        }
      } else {
        addEditorButtonsContainer($elem, fileId, buttons);
        $(".previewBtn .editorButtonContainer").addClass("dropup");
        // Send event to get info about current open provider
        publishDocument(fileId, {
          "type" : DOCUMENT_PREVIEW_OPENED,
          "fileId" : fileId,
          "workspace" : workspace
        });
      }
    };
    
    /**
     * Adds the 'Edit Online' button to No-preview screen (from the activity stream) when it's loaded.
     */
    var tryAddEditorButtonNoPreview = function(attempts, delay, fileId, workspace, buttons) {
      var $elem = $("#documentPreviewContainer .navigationContainer.noPreview");
      if ($elem.length == 0 || !$elem.is(":visible")) {
        if (attempts > 0) {
          setTimeout(function() {
            tryAddEditorButtonNoPreview(attempts - 1, delay, fileId, workspace, buttons);
          }, delay);
        } else {
          log("Cannot find .noPreview element");
        }
      } else if ($elem.find("div.editorButtonContainer").length == 0) {
        var $detailContainer = $elem.find(".detailContainer");
        var $downloadBtn = $detailContainer.find(".uiIconDownload").closest("a.btn");
        var $target = $("<div style='display: inline;'></div>");
        if ($downloadBtn.length != 0) {
          $downloadBtn.after($target);
        } else {
          $detailContainer.append($target);
        }
        addEditorButtonsContainer($target, fileId, buttons);
        // Send event to get info about current open provider
        publishDocument(fileId, {
          "type" : DOCUMENT_PREVIEW_OPENED,
          "fileId" : fileId,
          "workspace" : workspace
        });
      }
    };

    /**
     * Subscribes the document and reacts to the events.
     * Providers param is optional (used for Documents app)
     */
    var subscribeDocument = function(fileId, providers) {
      // Use only one channel for one document
      if (subscribedDocuments.fileId) {
        return;
      }
      var subscription = cometd.subscribe("/eXo/Application/documents/" + fileId, function(message) {
        // Channel message handler
        var result = tryParseJson(message);
        switch(result.type) {
          case DOCUMENT_OPENED: {
            $('.editorButton[data-provider!="' + result.provider + '"][data-fileId="' + result.fileId + '"]').each(function(){
              $(this).addClass("disabledProvider");
            });
            // Web UI buttons
            if (providers) {
              var allProviders = providers.slice();
              var index = allProviders.indexOf(result.provider);
              if (index !== -1) allProviders.splice(index, 1);
              allProviders.forEach(provider => {
                $( "i[class*='uiIconEcms" + provider + "Open' i]").each(function(){
                  $(this).parents(':eq(1)').addClass("disabledProvider");
                });
              });
            }
          } break;
          case LAST_EDITOR_CLOSED: {
            $('.editorButton[data-provider!="' + result.provider + '"][data-fileId="' + result.fileId + '"]').each(function(){
              $(this).removeClass("disabledProvider");
            });
             // Web UI buttons
            if (providers) {
              var allProviders = providers.slice();
              var index = allProviders.indexOf(result.provider);
        if (index !== -1) allProviders.splice(index, 1);
              allProviders.forEach(provider => {
                $( "i[class*='uiIconEcms" + provider + "Open' i]").each(function(){
                  $(this).parents(':eq(1)').removeClass("disabledProvider");
                });
              });
            }
          } break;
          case CURRENT_PROVIDER_INFO: {
            if(result.provider && result.provider != "null") {
              $('.editorButton[data-provider!="' + result.provider + '"][data-fileId="' + result.fileId + '"]').each(function(){
                $(this).addClass("disabledProvider");
              });
            }
           break;
        }}
      }, cometdContext, function(subscribeReply) {
        // Subscription status callback
        if (subscribeReply.successful) {
          // The server successfully subscribed this client to the channel.
          log("Document updates subscribed successfully: " + JSON.stringify(subscribeReply));
          subscribedDocuments.fileId = subscription;
        } else {
          var err = subscribeReply.error ? subscribeReply.error : (subscribeReply.failure ? subscribeReply.failure.reason
              : "Undefined");
          log("Document updates subscription failed for " + fileId, err);
        }
      });
    };

    var unsubscribeDocument = function(fileId) {
      var subscription = subscribedDocuments.fileId;
      if (subscription) {
        cometd.unsubscribe(subscription, {}, function(unsubscribeReply) {
          if (unsubscribeReply.successful) {
            // The server successfully unsubscribed this client to the channel.
            log("Document updates unsubscribed successfully for: " + fileId);
            delete subscribedDocuments.fileId;
          } else {
            var err = unsubscribeReply.error ? unsubscribeReply.error
                : (unsubscribeReply.failure ? unsubscribeReply.failure.reason : "Undefined");
            log("Document updates unsubscription failed for " + fileId, err);
          }
        });
      }
    };
    
    var publishDocument = function(fileId, data) {
      var deferred = $.Deferred();
      cometd.publish("/eXo/Application/documents/" + fileId, data, cometdContext, function(publishReply) {
        // Publication status callback
        if (publishReply.successful) {
          deferred.resolve();
          // The server successfully subscribed this client to the channel.
          log("Document update published successfully: " + JSON.stringify(publishReply));
        } else {
          deferred.reject();
          var err = publishReply.error ? publishReply.error : (publishReply.failure ? publishReply.failure.reason : "Undefined");
          log("Document updates publication failed for " + fileId, err);
        }
      });
      return deferred;
    };
    
    this.init = function(userId, cometdConf) {
      if (cometdConf) {
        cCometD.configure({
          "url" : prefixUrl + cometdConf.path,
          "exoId" : userId,
          "exoToken" : cometdConf.token,
          "maxNetworkDelay" : 30000,
          "connectTimeout" : 60000
        });
        cometdContext = {
          "exoContainerName" : cometdConf.containerName,
          "provider" : cometdConf.provider,
          "workspace" : cometdConf.workspace
        };
        cometd = cCometD;
      }
    };
    
    this.initExplorer = function(fileId, providers, currentProvider) {
      subscribeDocument(fileId, providers);
      // Web UI buttons
      if (providers && currentProvider) {
        var allProviders = providers.slice();
        var index = allProviders.indexOf(currentProvider);
        if (index !== -1) allProviders.splice(index, 1);
        allProviders.forEach(provider => {
          $( "i[class*='uiIconEcms" + provider + "Open' i]").each(function(){
            $(this).parents(':eq(1)').addClass("disabledProvider");
          });
        });
      }

    };
    
    /**
     * Inits editor buttons on DocumentUIActivity.
     * 
     */
    this.initActivityButtons = function(config) {
      var buttons = buttonsFns.slice();
      if(buttons.length == 0) {
        return;
      }
      log("Init Activity buttons: " + JSON.stringify(buttons));
      // Sort buttons in user prefference order
      if(config.prefferedProvider != null) {
        buttons.forEach(function(item,i){
          if(item.provider === config.prefferedProvider){
            buttons.splice(i, 1);
            buttons.unshift(item);
          }
        });
      }
      var $target = $("#activityContainer" + config.activityId).find("div[id^='ActivityContextBox'] > .actionBar .statusAction.pull-left");
      addEditorButtonsContainer($target, config.fileId, buttons);
      // Disable editor buttons if the document is currently editing in one of editors.
      if(config.currentProvider != null) {
        $('.editorButton[data-provider!="' + config.currentProvider + '"][data-fileId="' + config.fileId + '"]').each(function(){
          $(this).addClass("disabledProvider");
        });
      }
      subscribeDocument(config.fileId);
    };
    
    /**
     * Inits buttons on document preview.
     * 
     */
    this.initPreviewButtons = function(config) {
      var buttons = buttonsFns.slice();
      if(buttons.length == 0) {
        return;
      }
      log("Init preview buttons: " + JSON.stringify(buttons));
      var clickSelector = "#Preview" + config.activityId + "-" + config.index;
      if(config.prefferedProvider != null) {
        buttons.forEach(function(item,i){
          if(item.provider === config.prefferedProvider){
            buttons.splice(i, 1);
            buttons.unshift(item);
          }
        });
      }
      $(clickSelector).click(function() {
        // We set timeout here to avoid the case when the element is rendered but is going to be updated soon
        setTimeout(function() {
          tryAddEditorButtonToPreview(100, 100, config.fileId, config.workspace, buttons);
          // We need wait for about 2min when doc cannot generate its preview
          tryAddEditorButtonNoPreview(600, 250, config.fileId, config.workspace, buttons);
        }, 100);
        if(config.currentProvider != null) {
          $('.editorButton[data-provider!="' + config.currentProvider + '"][data-fileId="' + config.fileId + '"]').each(function(){
            $(this).addClass("disabledProvider");
          });
        }
        subscribeDocument(config.fileId);
      });
    };
    
    /**
     * API for providers to add their editor buttons.
     * 
     */
    this.addCreateButtonFn = function(provider, createButtonFn) {
      var buttonFn = { "provider" : provider, "createButtonFn" : createButtonFn };
      var index = buttonsFns.findIndex(elem => elem.provider === provider);
      if (index === -1) {
        buttonsFns.push(buttonFn);
      } else {
        buttonsFns[index] = buttonFn;
      }
    };
    
    this.onEditorOpen = function(fileId, workspace, provider) {
      log("Editor opened. Provider: " + provider + ", fileId: " + fileId);
      publishDocument(fileId, {
        "type" : DOCUMENT_OPENED,
        "provider" : provider,
        "fileId" : fileId,
        "workspace" : workspace
      });
      // subsribe to track opened editors on server-side
      var subscription = cometd.subscribe("/eXo/Application/documents/" + fileId, function(message) { }, cometdContext, function(subscribeReply) {});
    };

    this.onEditorClose = function(fileId, workspace, provider) {
      log("Editor closed. Provider: " + provider + ", fileId: " + fileId);
      /*publishDocument(fileId, {
        "type" : DOCUMENT_CLOSED,
        "provider" : provider,
        "fileId" : fileId,
        "workspace" : workspace
      });*/
    };
   
    /**
     * Clears buttonsFns
     * 
     */
    this.resetButtons = function() {
      buttonsFns = [];
    }
    
  }
  return new EditorButtons();

})($, cCometD);