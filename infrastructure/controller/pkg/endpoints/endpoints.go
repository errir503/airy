package endpoints

import (
	"context"
	"encoding/json"
	v1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"log"
	"net/http"
)

type Server struct {
	clientSet *kubernetes.Clientset
}

type ServicesResponse struct {
	Services map[string]Service `json:"services"`
}

type Service struct {
	Enabled   bool   `json:"enabled"`
	Component string `json:"component"`
}

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// Only return apps that are part of a component
	deployments, _ := s.clientSet.AppsV1().Deployments("default").List(context.TODO(), v1.ListOptions{
		LabelSelector: "core.airy.co/component",
	})

	componentsMap := make(map[string]Service)
	for _, deployment := range deployments.Items {
		app := deployment.ObjectMeta.Name
		componentsMap[app] = Service{
			Enabled:   *deployment.Spec.Replicas > 0,
			Component: deployment.ObjectMeta.Labels["core.airy.co/component"],
		}
	}

	resp, _ := json.Marshal(&ServicesResponse{Services: componentsMap})
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write(resp)
}

func Serve(clientSet *kubernetes.Clientset) {
	s := &Server{clientSet: clientSet}
	http.Handle("/services", s)
	log.Fatal(http.ListenAndServe(":8080", nil))
}
