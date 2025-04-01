package main

import (
	"errors"
	"fmt"
	"net/http"
	"strings"
	"tts/src/storage"
	"tts/src/tts"

	"github.com/Azure/azure-sdk-for-go/sdk/azcore"
	"github.com/gin-gonic/gin"
)

type ProcessRequest struct {
	Engine string     `json:"engine"`
	Gender string     `json:"gender"`
	Words  []tts.Word `json:"words"`
}

func main() {

	engine, err := NewEngine("azure", nil)
	if err != nil {
		panic(err)
	}
	defer engine.Close()

	router := gin.Default()

	router.POST("/api/v1/process", handleProcessRequest)
	router.GET("/api/v1/get/:id", handleGetRequest)

	fmt.Println("[TTS-debug] Starting server")
	router.Run(":8081")
}

func handleProcessRequest(c *gin.Context) {
	var req ProcessRequest
	if err := c.BindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	var engine *Engine
	var err error
	switch strings.ToLower(req.Engine) {
	case "google":
		engine, err = NewEngine("google", nil)
	case "azure":
		engine, err = NewEngine("azure", nil)
	default:
		engine, err = NewEngine("azure", nil)
	}
	if err != nil {
		panic(err)
	}
	defer engine.Close()

	_, err = engine.BatchProcessWords(req.Words, req.Gender)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, gin.H{})
}

func handleGetRequest(c *gin.Context) {
	// Get ID from URL path parameter instead of JSON body
	id := c.Param("id")

	// Connect to db
	blobDB, err := storage.ConnectToAzurite()
	if err != nil {
		c.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{
			"error": fmt.Sprintf("Failed to retrieve audio: %v", err),
		})
	}

	// Get the audio data
	audioData, err := blobDB.GetTTSAudio(id)
	if err != nil {
		if isNotFoundError(err) { // Implement this error check
			c.AbortWithStatusJSON(http.StatusNotFound, gin.H{"error": "Audio file not found"})
			return
		}
		c.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{
			"error": fmt.Sprintf("Failed to retrieve audio: %v", err),
		})
		return
	}

	// Set headers for file download
	c.Header("Content-Description", "File Transfer")
	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%s.wav", id))
	c.Header("Content-Type", "audio/wav")
	c.Header("Content-Length", fmt.Sprintf("%d", len(audioData)))

	// Send the binary data
	c.Data(http.StatusOK, "audio/wav", audioData)
}

func isNotFoundError(err error) bool {
	var respErr *azcore.ResponseError
	if errors.As(err, &respErr) {
		return respErr.StatusCode == http.StatusNotFound
	}
	return false
}
