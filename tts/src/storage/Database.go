package storage

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"time"

	"github.com/Azure/azure-sdk-for-go/sdk/azcore"
	"github.com/Azure/azure-sdk-for-go/sdk/storage/azblob"
	"github.com/Azure/azure-sdk-for-go/sdk/storage/azblob/blob"
)

const (
	DefaultAzuriteAccountName string = "devstoreaccount1"
	DefaultAzuriteAccountKey  string = "Eby8vdM02xNOcqFevkbZzGvAafMNCxF+GB0Wm8w5RkWIFhZ4IRHxuRHIFw=="
	DefaultAzuriteBlobURL     string = "http://azurite:10000/%s"
)

type BlobDatabase interface {
	InsertTTSAudio(filename string, data []byte) (string, error)
}

type AzureBlobDatabase struct {
	serviceClient *azblob.Client
	serviceURL    string
	containerName string
}

type AzureBlobOptions struct {
	AccountName   string
	AccountKey    string
	BlobURL       string
	ContainerName string
}

// NewAzureBlobDatabase creates a new Azure Blob Database client using azblob SDK
func NewAzureBlobDatabase(options AzureBlobOptions) (*AzureBlobDatabase, error) {
	if options.AccountName == "" {
		options.AccountName = DefaultAzuriteAccountName
	}
	if options.AccountKey == "" {
		options.AccountKey = DefaultAzuriteAccountKey
	}
	if options.BlobURL == "" {
		options.BlobURL = fmt.Sprintf(DefaultAzuriteBlobURL, options.AccountName)
	}
	if options.ContainerName == "" {
		options.ContainerName = "tmp"
	}

	cred, err := azblob.NewSharedKeyCredential(options.AccountName, options.AccountKey)
	if err != nil {
		return nil, fmt.Errorf("failed to create credentials: %w", err)
	}

	serviceClient, err := azblob.NewClientWithSharedKeyCredential(
		options.BlobURL,
		cred,
		nil,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create blob service client: %w", err)
	}

	db := &AzureBlobDatabase{
		serviceClient: serviceClient,
		serviceURL:    options.BlobURL,
		containerName: options.ContainerName,
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	// Create container if not exists
	_, err = db.serviceClient.CreateContainer(ctx, options.ContainerName, nil)
	if err != nil {
		if !isContainerExistsError(err) {
			return nil, fmt.Errorf("failed to create container: %w", err)
		}
	}

	return db, nil
}

func ConnectToAzurite() (*AzureBlobDatabase, error) {
	host := "localhost"
	// For Docker environment, get the host from environment variable
	// or use Docker service name if running in Docker Compose
	if os.Getenv("DOCKER_ENV") == "true" {
		host = "mingxue_azurite"
	}

	options := AzureBlobOptions{
		BlobURL:       fmt.Sprintf("http://%s:10000/%s", host, DefaultAzuriteAccountName),
		ContainerName: "tts-audio",
	}
	return NewAzureBlobDatabase(options)
}

func (db *AzureBlobDatabase) InsertTTSAudio(filename string, data []byte) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	_, err := db.serviceClient.UploadBuffer(
		ctx,
		db.containerName,
		filename,
		data,
		&azblob.UploadBufferOptions{
			HTTPHeaders: &blob.HTTPHeaders{
				BlobContentType: ptrTo("audio/wav"),
			},
		},
	)
	if err != nil {
		return "", fmt.Errorf("failed to upload audio: %w", err)
	}

	// Generate the public URL using stored service URL
	return fmt.Sprintf("%s/%s/%s",
		db.serviceURL,
		db.containerName,
		filename), nil
}

// Add this method to your AzureBlobDatabase struct
func (db *AzureBlobDatabase) GetTTSAudio(id string, sentence bool) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	// Construct blob path: "tts/{id}.wav"
	path := ""
	if sentence {
		path = "tts/sentence/"
	} else {
		path = "tts/word/"
	}
	blobName := fmt.Sprintf("%s%s.wav", path, id)

	// Download the blob
	resp, err := db.serviceClient.DownloadStream(ctx, db.containerName, blobName, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to download audio: %w", err)
	}

	// Read the response body
	downloadedData, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	return downloadedData, nil
}

func isContainerExistsError(err error) bool {
	var storageErr *azcore.ResponseError
	if errors.As(err, &storageErr) {
		// Use direct string comparison for Azurite error code
		return storageErr.ErrorCode == "ContainerAlreadyExists"
	}
	return false
}

func ptrTo[T any](v T) *T {
	return &v
}
